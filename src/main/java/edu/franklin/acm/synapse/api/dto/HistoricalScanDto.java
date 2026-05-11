package edu.franklin.acm.synapse.api.dto;

public record HistoricalScanDto(
        long jobId,
        String guildId,
        String status,
        String startedAt,
        String completedAt,
        int checkpointCount,
        String errorMessage) {
}
