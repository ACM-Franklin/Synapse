package edu.franklin.acm.synapse.api.resource;

import java.util.ArrayList;
import java.util.List;

import edu.franklin.acm.synapse.api.auth.AuthGuard;
import edu.franklin.acm.synapse.api.auth.UserSession;
import edu.franklin.acm.synapse.api.dto.MemberDashboardDto;
import edu.franklin.acm.synapse.api.service.StatsQueryDao;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/members")
@Produces(MediaType.APPLICATION_JSON)
public class MemberResource {

    @Inject AuthGuard guard;
    @Inject StatsQueryDao stats;

    @GET
    @Path("/me/dashboard")
    public MemberDashboardDto myDashboard() {
        UserSession session = guard.requireMember();
        StatsQueryDao.MemberProfile profile = stats.findProfileByExtId(session.userExtId());
        if (profile == null) {
            return new MemberDashboardDto(
                String.valueOf(session.userExtId()),
                session.globalName() != null ? session.globalName() : session.username(),
                session.avatarHash(),
                0,
                0,
                1,
                0,
                0,
                0,
                0,
                true,
                List.of());
        }

        int rank = stats.rankByPrimaryCurrency(profile.pCurrency());
        int messages = stats.countMessagesByMember(profile.memberId());
        int reactions = stats.countReactionsByMember(profile.memberId());
        int voiceMinutes = stats.sumVoiceMinutesByMember(profile.memberId());
        List<StatsQueryDao.RewardTraceRow> rewards = stats.recentRewardsByMember(profile.memberId(), 10);

        List<MemberDashboardDto.RewardTraceDto> traceDtos = new ArrayList<>(rewards.size());
        for (StatsQueryDao.RewardTraceRow r : rewards) {
            traceDtos.add(new MemberDashboardDto.RewardTraceDto(
                    r.ruleName(),
                    r.currencyType(),
                    r.amount(),
                    r.transactionType(),
                    r.subjectType(),
                    r.subjectExtId() == null ? null : String.valueOf(r.subjectExtId()),
                    r.createdAt()));
        }

        return new MemberDashboardDto(
                String.valueOf(profile.userExtId()),
                profile.displayName(),
                profile.avatarHash(),
                profile.pCurrency(),
                profile.sCurrency(),
                profile.level(),
                rank,
                messages,
                reactions,
                voiceMinutes,
                false,
                traceDtos);
    }
}
