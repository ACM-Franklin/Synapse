package edu.franklin.acm.synapse.activity.message;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * Persists message event data with upsert semantics on Discord message ID (ext_id).
 * Ensures a single row per message; message edits overwrite mutable fields in place.
 */
public interface MessageEventDao {

    /**
     * Inserts a new message event or updates the existing row if the Discord message
     * ID already exists. Only mutable fields are updated on conflict; immutable fields
     * (event_id, ext_id, author_is_bot, type) retain their original values.
     * 
     * @param messageEvent the message event data to persist
     * @return the internal row ID (auto-generated)
     */
    @SqlQuery("""
            INSERT INTO messages (
                event_id, ext_id, content, content_length, type,
                is_reply, referenced_message_ext_id, spawned_thread,
                edited_at, has_attachments, attachment_count, reaction_count,
                mention_user_count, mention_role_count, mention_channel_count,
                mention_everyone, is_tts, is_pinned, has_stickers, has_poll,
                embed_count, is_voice_message, flags, author_is_bot
            ) VALUES (
                :eventId, :extId, :content, :contentLength, :type,
                :isReply, :referencedMessageExtId, :spawnedThread,
                :editedAt, :hasAttachments, :attachmentCount, :reactionCount,
                :mentionUserCount, :mentionRoleCount, :mentionChannelCount,
                :mentionEveryone, :isTts, :isPinned, :hasStickers, :hasPoll,
                :embedCount, :isVoiceMessage, :flags, :authorIsBot
            )
            ON CONFLICT (ext_id) DO UPDATE SET
                content = :content,
                content_length = :contentLength,
                edited_at = :editedAt,
                has_attachments = :hasAttachments,
                attachment_count = :attachmentCount,
                reaction_count = :reactionCount,
                mention_user_count = :mentionUserCount,
                mention_role_count = :mentionRoleCount,
                mention_channel_count = :mentionChannelCount,
                mention_everyone = :mentionEveryone,
                is_tts = :isTts,
                is_pinned = :isPinned,
                has_stickers = :hasStickers,
                has_poll = :hasPoll,
                embed_count = :embedCount,
                is_voice_message = :isVoiceMessage,
                flags = :flags
            RETURNING id
            """)
    long upsert(@BindMethods MessageEvent messageEvent);

    /**
     * Looks up the internal row ID by Discord message ID.
     * 
     * @param extId the Discord message ID (snowflake)
     * @return the internal row ID, or {@code null} if not found
     */
    @SqlQuery("SELECT id FROM messages WHERE ext_id = :extId")
    Long findIdByExtId(@Bind("extId") long extId);
}
