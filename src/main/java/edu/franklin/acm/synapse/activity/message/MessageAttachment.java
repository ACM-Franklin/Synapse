package edu.franklin.acm.synapse.activity.message;

import net.dv8tion.jda.api.entities.Message;

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

    public static MessageAttachment fromDiscord(long messageEventId, Message.Attachment a) {
        return new MessageAttachment(
                0L,
                messageEventId,
                a.getIdLong(),
                a.getFileName(),
                a.getDescription(),
                a.getContentType(),
                a.getSize(),
                a.getWidth(),
                a.getHeight(),
                a.getDuration() > 0 ? a.getDuration() : null
        );
    }
}
