package edu.franklin.acm.synapse.bot;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.guild.SynapseStatisticsDao;
import edu.franklin.acm.synapse.scanners.GuildLiveScanner;
import edu.franklin.acm.synapse.scanners.HistoricalScanCoordinator;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * Application entry point and Discord bot lifecycle manager.
 * 
 * <p>Initializes the JDA Discord gateway connection with required intents,
 * registers event listeners, and optionally triggers a historical backfill scan
 * of guild data on startup.
 * 
 * <p>Managed as a {@code @Startup} singleton that begins initialization immediately
 * when the application boots.
 */
@Startup
@ApplicationScoped
public class SynapseBot {
    private static final Logger log = LoggerFactory.getLogger(SynapseBot.class);

    // Configuration properties
    private final String discordToken;
    private final boolean historicalScanEnabled;
    private final long guildId;

    // Event scanners
    private final HistoricalScanCoordinator historicalScanCoordinator;
    private final GuildLiveScanner guildLiveScanner;

    @Inject SynapseStatisticsDao statisticsDao;

    // Gateway connection (initialized on startup)
    private JDA jda;

    /**
     * Constructs the bot with configuration and scanner dependencies.
     * 
     * @param discordToken              the Discord bot token (from discord.token property)
    * @param historicalScanCoordinator persisted historical scan coordinator
     * @param guildLiveScanner          scanner for live Discord events (guild members, messages, etc.)
     * @param scanHistorical            whether to run a full historical scan on startup
     *                                  (from historical.scan.enabled property, default false)
     * @param guildId                   the guild ID to scan historically, if enabled
     *                                  (from guild.id property, default 0)
     */
    public SynapseBot(
            @ConfigProperty(name = "synapse.discord.token") String discordToken,
            HistoricalScanCoordinator historicalScanCoordinator,
            GuildLiveScanner guildLiveScanner,
            @ConfigProperty(name = "synapse.discord.scan-historical", defaultValue = "false") boolean scanHistorical,
            @ConfigProperty(name = "synapse.discord.guild.id", defaultValue = "0") long guildId) {
        this.discordToken = discordToken;
        this.historicalScanCoordinator = historicalScanCoordinator;
        this.guildLiveScanner = guildLiveScanner;
        this.historicalScanEnabled = scanHistorical;
        this.guildId = guildId;
    }

    /**
     * Starts the Discord gateway connection and optionally initiates historical scanning.
     * 
     * <p>This method blocks until the bot reaches the Ready state before proceeding,
     * ensuring all guilds are available. If historical scanning is enabled and the
     * guild ID is valid, it launches an asynchronous historical scan.
     * 
     * @throws InterruptedException if the gateway connection is interrupted while waiting
     */
    @PostConstruct
    @SuppressWarnings("unused")
    void start() throws InterruptedException {
        log.info("SynapseBot starting up...");
        jda = JDABuilder.createDefault(discordToken)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MESSAGE_REACTIONS,
                        GatewayIntent.GUILD_VOICE_STATES
                )
                .addEventListeners(guildLiveScanner)
                .build();

        jda.awaitReady();
        statisticsDao.recordStartup();

        // Reconcile database state with live guild
        if (guildId > 0) {
            var guild = jda.getGuildById(guildId);
            if (guild != null) {
                try {
                    guildLiveScanner.reconcile(guild);
                } catch (Exception e) {
                    log.error("Startup reconciliation failed for guild {}", guildId, e);
                }
            } else {
                log.warn("Cannot reconcile: guild {} not available to this bot", guildId);
            }
        }

        if (historicalScanEnabled) {
            performHistoricalScan();
        }
    }

    /**
     * Initiates a historical backfill scan for the configured guild.
     * 
     * <p>Validates that the guild ID is set and accessible to this bot before
     * starting the scan. The scan runs asynchronously and logs completion or errors.
     */
    private void performHistoricalScan() {
        if (guildId <= 0) {
            log.warn("historical.scan.enabled=true but guild.id is not set to a valid guild ID");
            return;
        }

        var guild = jda.getGuildById(guildId);
        if (guild == null) {
            log.warn("Configured guild.id {} is not available to this bot", guildId);
            return;
        }

        log.info("Starting historical scan for guild {} ({})", guild.getName(), guild.getIdLong());
        long jobId = historicalScanCoordinator.startGuildScan(guild);
        log.info("Historical scan job {} started for guild {}", jobId, guild.getIdLong());
    }

    /**
     * Gracefully shuts down the Discord gateway connection.
     * 
     * <p>Called when the application terminates.
     */
    @PreDestroy
    @SuppressWarnings("unused")
    void stop() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    /**
     * Retrieves the bot's latency to Discord's gateway.
     * 
     * @return the gateway ping in milliseconds, or -1 if the connection is not ready
     */
    public long ping() {
        if (jda == null) return -1;
        return jda.getGatewayPing();
    }
}
