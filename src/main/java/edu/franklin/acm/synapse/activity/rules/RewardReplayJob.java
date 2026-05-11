package edu.franklin.acm.synapse.activity.rules;

public record RewardReplayJob(
        long id,
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