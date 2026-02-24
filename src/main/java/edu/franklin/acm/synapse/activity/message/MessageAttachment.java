package edu.franklin.acm.synapse.activity.message;

/**
 * One attachment on a message. FK to message_events.
 */
public record MessageAttachment(
        long id,
        long messageEventId,
        long extId,
        String filename,
        String description,
        String contentType,
        int size,
        int width,
        int height,
        Double durationSecs) {
}
