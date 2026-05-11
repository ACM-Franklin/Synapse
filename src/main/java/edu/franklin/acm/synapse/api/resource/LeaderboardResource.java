package edu.franklin.acm.synapse.api.resource;

import java.util.ArrayList;
import java.util.List;

import edu.franklin.acm.synapse.api.auth.AuthGuard;
import edu.franklin.acm.synapse.api.dto.LeaderboardDto;
import edu.franklin.acm.synapse.api.service.StatsQueryDao;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/leaderboard")
@Produces(MediaType.APPLICATION_JSON)
public class LeaderboardResource {

    private static final int MAX_LIMIT = 100;

    @Inject AuthGuard guard;
    @Inject StatsQueryDao stats;

    @GET
    public LeaderboardDto get(
            @QueryParam("currency") @DefaultValue("primary") String currency,
            @QueryParam("limit") @DefaultValue("25") int limit) {
        guard.requireMember();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new WebApplicationException(
                    "limit must be 1.." + MAX_LIMIT,
                    Response.Status.BAD_REQUEST);
        }

        List<StatsQueryDao.LeaderboardRow> rows = switch (currency) {
            case "primary" -> stats.topByPrimaryCurrency(limit);
            case "secondary" -> stats.topBySecondaryCurrency(limit);
            default -> throw new WebApplicationException(
                    "currency must be 'primary' or 'secondary'",
                    Response.Status.BAD_REQUEST);
        };

        List<LeaderboardDto.Entry> entries = new ArrayList<>(rows.size());
        int rank = 1;
        for (StatsQueryDao.LeaderboardRow row : rows) {
            entries.add(new LeaderboardDto.Entry(
                    rank++,
                    String.valueOf(row.userExtId()),
                    row.displayName(),
                    row.avatarHash(),
                    row.amount(),
                    row.level()));
        }
        return new LeaderboardDto(currency, limit, entries);
    }
}
