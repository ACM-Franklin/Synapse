package edu.franklin.acm.synapse.api.resource;

import edu.franklin.acm.synapse.activity.guild.GuildMetadataDao;
import edu.franklin.acm.synapse.activity.guild.HistoricalScanJob;
import edu.franklin.acm.synapse.api.auth.AuthConfig;
import edu.franklin.acm.synapse.api.auth.AuthGuard;
import edu.franklin.acm.synapse.api.auth.SessionStore;
import edu.franklin.acm.synapse.api.dto.SystemStatusDto;
import edu.franklin.acm.synapse.api.service.AdminOperationsService;
import edu.franklin.acm.synapse.api.service.RuleQueryService;
import edu.franklin.acm.synapse.api.service.StatsQueryDao;
import edu.franklin.acm.synapse.bot.SynapseBot;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/system/status")
@Produces(MediaType.APPLICATION_JSON)
public class SystemStatusResource {

    @Inject AuthGuard guard;
    @Inject AuthConfig config;
    @Inject SessionStore sessions;
    @Inject GuildMetadataDao guildMetadataDao;
    @Inject StatsQueryDao stats;
    @Inject RuleQueryService rules;
    @Inject AdminOperationsService admin;

    @GET
    public SystemStatusDto get() {
        guard.requireAdmin();

        Long guildExtId = guildMetadataDao.getExtId();
        String guildName = guildMetadataDao.getName();
        SynapseBot bot = admin.bot();
        boolean discordConnected = bot != null && bot.isConnected();
        long gatewayPing = bot != null ? bot.ping() : -1L;

        HistoricalScanJob latest = stats.findLatestHistoricalScanJob();

        return new SystemStatusDto(
                guildExtId == null ? null : String.valueOf(guildExtId),
                guildName,
                config.isOauthConfigured(),
                discordConnected,
                gatewayPing,
                sessions.activeSessionCount(),
                sessions.ttlSeconds(),
                config.adminRoleIds().size(),
                stats.countActiveMembers(),
                stats.countActiveChannels(),
                stats.countEnabledRules(),
                rules.countInvalid(),
                latest == null ? null : latest.id(),
                latest == null ? null : latest.status());
    }
}
