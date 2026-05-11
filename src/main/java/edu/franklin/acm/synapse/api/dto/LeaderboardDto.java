package edu.franklin.acm.synapse.api.dto;

import java.util.List;

public record LeaderboardDto(
        String currencyType,
        int limit,
        List<Entry> entries) {

    public record Entry(
            int rank,
            String userId,
            String displayName,
            String avatarHash,
            int amount,
            int level) {
    }
}
