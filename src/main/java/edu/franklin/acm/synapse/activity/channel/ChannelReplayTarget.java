package edu.franklin.acm.synapse.activity.channel;

public record ChannelReplayTarget(
        long id,
        long extId,
        String type,
        Long categoryExtId) {
}