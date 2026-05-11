package edu.franklin.acm.synapse.api.dto;

public record GuildSummaryDto(
        String guildId,
        String guildName,
        int memberCount,
        int activeChannelCount,
        int activeRoleCount) {
}
