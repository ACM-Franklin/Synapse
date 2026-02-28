package edu.franklin.acm.synapse.activity.message;

import net.dv8tion.jda.api.entities.emoji.Emoji;

/**
 * One distinct emoji reaction on a message. FK to messages.
 */
public record MessageReaction(
        long id,
        long messageId,
        String emojiName,
        Long emojiExtId,
        int count,
        int burstCount) {

    public static MessageReaction fromDiscord(long messageId,
                                              net.dv8tion.jda.api.entities.MessageReaction r) {
        return new MessageReaction(
                0L,
                messageId,
                r.getEmoji().getName(),
                r.getEmoji().getType() == Emoji.Type.CUSTOM
                        ? r.getEmoji().asCustom().getIdLong() : null,
                r.hasCount() ? r.getCount() : 0,
                r.hasCount() ? r.getCount(net.dv8tion.jda.api.entities.MessageReaction.ReactionType.SUPER) : 0
        );
    }
}
