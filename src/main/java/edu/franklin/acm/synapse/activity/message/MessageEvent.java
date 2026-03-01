package edu.franklin.acm.synapse.activity.message;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import net.dv8tion.jda.api.entities.Message;

/**
 * Normalized representation of a Discord message in the Event Lake. One row per
 * message. Edits upsert — only current state is stored.
 */
public record MessageEvent(
        long id,
        long eventId,
        long extId,
        Long threadId,
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
        String createdAt,
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

    public static MessageEvent fromDiscord(long eventId, Long threadId, Message m) {
        final var mentions = m.getMentions();
        final var referencedMessage = m.getReferencedMessage();
        final var timeEdited = m.getTimeEdited();
        final var content = m.getContentRaw();

        return new MessageEvent(
                0L,
                eventId,
                m.getIdLong(),
                threadId,
                m.getFlagsRaw(),
                content.length(),
                m.getType().getId(),
                m.getAttachments().size(),
                m.getReactions().stream().mapToInt(r -> r.getCount()).sum(),
                mentions.getUsers().size(),
                mentions.getRoles().size(),
                mentions.getChannels().size(),
                m.getEmbeds().size(),
                content,
                referencedMessage != null ? referencedMessage.getIdLong() : null,
                timeEdited != null ? LocalDateTime.ofInstant(timeEdited.toInstant(), ZoneOffset.UTC) : null,
                LocalDateTime.ofInstant(m.getTimeCreated().toInstant(), ZoneOffset.UTC).toString(),
                referencedMessage != null,
                m.getStartedThread() != null,
                !m.getAttachments().isEmpty(),
                mentions.mentionsEveryone(),
                m.isTTS(),
                m.isPinned(),
                !m.getStickers().isEmpty(),
                m.getPoll() != null,
                m.isVoiceMessage(),
                m.getAuthor().isBot()
        );
    }
}
