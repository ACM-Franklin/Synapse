package edu.franklin.acm.synapse.api.dto;

public record ReplayJobDto(
        long jobId,
        String status,
        int batchSize,
        int batchesProcessed,
        int scannedCount,
        int replayedCount,
        int failedCount,
        long lastMessageId,
        String startedAt,
        String completedAt,
        String errorMessage) {
}