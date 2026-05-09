package edu.franklin.acm.synapse.activity.message;

public record MessageDeletionTarget(
        long messageId,
        long eventId,
        long memberId,
        Long channelId) {
}
