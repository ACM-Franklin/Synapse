package edu.franklin.acm.synapse.activity.message;

/**
 * One distinct emoji reaction on a message. FK to message_events.
 */
public record MessageReaction(
        long id,
        long messageEventId,
        String emojiName,
        Long emojiExtId,
        int count,
        int burstCount) {
}
