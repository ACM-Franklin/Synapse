package edu.franklin.acm.synapse.activity.message;

import net.dv8tion.jda.api.entities.Message;

/**
 * One attachment on a message. FK to messages.
 */
public record MessageAttachment(
        long id,
        long messageId,
        long extId,
        String filename,
        String description,
        String contentType,
        int size,
        int width,
        int height,
        Double durationSecs) {

    public static MessageAttachment fromDiscord(long messageId, Message.Attachment a) {
        return new MessageAttachment(
                0L,
                messageId,
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
