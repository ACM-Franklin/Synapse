package edu.franklin.acm.synapse.api.dto;

public record SystemStatusDto(
        String guildId,
        String guildName,
        boolean oauthConfigured,
        boolean discordConnected,
        long gatewayPingMs,
        int activeSessions,
        long sessionTtlSeconds,
        int adminRoleCount,
        int memberCount,
        int activeChannelCount,
        int rulesEnabled,
        int rulesInvalid,
        Long latestHistoricalScanJobId,
        String latestHistoricalScanStatus) {
}
