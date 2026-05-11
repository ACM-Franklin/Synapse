package edu.franklin.acm.synapse.rules.engine;

public record MessageRewardReplayRunSummary(
        int batchesProcessed,
        int scannedCount,
        int replayedCount,
        int failedCount,
        long lastMessageId) {
}