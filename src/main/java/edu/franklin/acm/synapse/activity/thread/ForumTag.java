package edu.franklin.acm.synapse.activity.thread;

import java.time.LocalDateTime;

/**
 * A tag defined on a ForumChannel. Forum posts (threads) can have up to 5 tags
 * applied from the set available on their parent forum.
 */
public record ForumTag(
        long id,
        long extId,
        long channelId,
        String name,
        String emojiName,
        Long emojiExtId,
        boolean isModerated,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
