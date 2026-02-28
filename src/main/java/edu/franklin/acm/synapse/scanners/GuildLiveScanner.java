package edu.franklin.acm.synapse.scanners;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.scanners.handlers.MemberEventHandler;
import edu.franklin.acm.synapse.scanners.handlers.MessageIngestionHandler;
import edu.franklin.acm.synapse.scanners.handlers.ReconciliationHandler;
import edu.franklin.acm.synapse.scanners.handlers.VoiceEventHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Thin JDA gateway dispatcher. Receives Discord events and delegates to
 * domain-specific handlers for persistence and rule evaluation.
 *
 * <p>Registered as a JDA {@link ListenerAdapter} by {@code SynapseBot}.
 */
@ApplicationScoped
public class GuildLiveScanner extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GuildLiveScanner.class);

    @Inject MessageIngestionHandler messageHandler;
    @Inject MemberEventHandler memberHandler;
    @Inject VoiceEventHandler voiceHandler;
    @Inject ReconciliationHandler reconciliationHandler;

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.isWebhookMessage()) return;
        if (!event.isFromGuild()) return;

        try {
            messageHandler.handle(event.getMessage());
        } catch (Exception e) {
            log.error("Failed to ingest live message {}", event.getMessage().getId(), e);
        }
    }

    @Override
    public void onGuildMemberUpdate(@NotNull GuildMemberUpdateEvent event) {
        try {
            memberHandler.handleUpdate(event.getMember());
        } catch (Exception e) {
            log.error("Failed to process member update for {}", event.getMember().getUser().getName(), e);
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        try {
            memberHandler.handleJoin(event.getMember());
        } catch (Exception e) {
            log.error("Failed to process member join for {}", event.getMember().getUser().getName(), e);
        }
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        try {
            memberHandler.handleLeave(event.getUser());
        } catch (Exception e) {
            log.error("Failed to process member remove for {}", event.getUser().getName(), e);
        }
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        try {
            voiceHandler.handle(event);
        } catch (Exception e) {
            log.error("Failed to process voice update for {}", event.getMember().getUser().getName(), e);
        }
    }

    /**
     * Reconciles database state with the live guild after a restart.
     * Called once by {@code SynapseBot} after the JDA gateway is ready.
     */
    public void reconcile(Guild guild) throws Exception {
        reconciliationHandler.reconcile(guild);
    }
}
