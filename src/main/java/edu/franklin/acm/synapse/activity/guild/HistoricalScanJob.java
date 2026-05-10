package edu.franklin.acm.synapse.activity.guild;

public record HistoricalScanJob(
        long id,
        long guildExtId,
        String status,
        String startedAt,
        String completedAt,
        int checkpointCount,
        String errorMessage) {
}