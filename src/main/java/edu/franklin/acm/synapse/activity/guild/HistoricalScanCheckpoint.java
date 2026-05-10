package edu.franklin.acm.synapse.activity.guild;

public record HistoricalScanCheckpoint(
        long id,
        long guildExtId,
        long channelExtId,
        long lastMessageExtId,
        String scannedAt) {
}