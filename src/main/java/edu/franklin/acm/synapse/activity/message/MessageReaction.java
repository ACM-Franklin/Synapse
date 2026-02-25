package edu.franklin.acm.synapse.activity.message;

import net.dv8tion.jda.api.entities.emoji.Emoji;

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

    public static MessageReaction fromDiscord(long messageEventId,
                                              net.dv8tion.jda.api.entities.MessageReaction r) {
        return new MessageReaction(
                0L,
                messageEventId,
                r.getEmoji().getName(),
                r.getEmoji().getType() == Emoji.Type.CUSTOM
                        ? r.getEmoji().asCustom().getIdLong() : null,
                r.getCount(),
                r.hasCount() ? r.getCount(net.dv8tion.jda.api.entities.MessageReaction.ReactionType.SUPER) : 0
        );
    }
}
