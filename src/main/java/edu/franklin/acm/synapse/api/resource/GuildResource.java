package edu.franklin.acm.synapse.api.resource;

import edu.franklin.acm.synapse.activity.guild.GuildMetadataDao;
import edu.franklin.acm.synapse.api.auth.AuthGuard;
import edu.franklin.acm.synapse.api.dto.GuildSummaryDto;
import edu.franklin.acm.synapse.api.service.StatsQueryDao;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/guild")
@Produces(MediaType.APPLICATION_JSON)
public class GuildResource {

    @Inject AuthGuard guard;
    @Inject GuildMetadataDao guildMetadataDao;
    @Inject StatsQueryDao stats;

    @GET
    @Path("/summary")
    public GuildSummaryDto summary() {
        guard.requireMember();
        Long guildExtId = guildMetadataDao.getExtId();
        String guildName = guildMetadataDao.getName();
        return new GuildSummaryDto(
                guildExtId == null ? null : String.valueOf(guildExtId),
                guildName,
                stats.countActiveHumanMembers(),
                stats.countActiveChannels(),
                stats.countActiveRoles());
    }
}
