package edu.franklin.acm.synapse.activity.message;

import java.time.LocalDateTime;

/**
 * Normalized representation of a Discord message in the Event Lake. One row per
 * message. Edits upsert — only current state is stored.
 */
public record MessageEvent(
        long id,
        long eventId,
        long extId,
        long flags,
        int contentLength,
        int type,
        int attachmentCount,
        int reactionCount,
        int mentionUserCount,
        int mentionRoleCount,
        int mentionChannelCount,
        int embedCount,
        String content,
        Long referencedMessageExtId,
        LocalDateTime editedAt,
        LocalDateTime createdAt,
        boolean isReply,
        boolean spawnedThread,
        boolean hasAttachments,
        boolean mentionEveryone,
        boolean isTts,
        boolean isPinned,
        boolean hasStickers,
        boolean hasPoll,
        boolean isVoiceMessage,
        boolean authorIsBot) {

}
