package edu.franklin.acm.synapse.api.dto;

import java.util.List;

public record MemberDashboardDto(
        String userId,
        String displayName,
        String avatarHash,
        int primaryCurrency,
        int secondaryCurrency,
        int level,
        int leaderboardRank,
        int messagesSent,
        int reactionsSent,
        int voiceMinutes,
        boolean pending,
        List<RewardTraceDto> recentRewards) {

    public record RewardTraceDto(
            String ruleName,
            String currencyType,
            int amount,
            String transactionType,
            String subjectType,
            String subjectExtId,
            String createdAt) {
    }
}
