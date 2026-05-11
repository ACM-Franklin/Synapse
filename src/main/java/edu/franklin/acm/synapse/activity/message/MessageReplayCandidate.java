package edu.franklin.acm.synapse.activity.message;

public record MessageReplayCandidate(
        long messageId,
        long messageExtId) {
}