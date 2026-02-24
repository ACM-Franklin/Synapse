package edu.franklin.acm.synapse.activity.message;

import java.util.List;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Writes per-emoji reaction rows for a message. Batch methods used by scanners
 * for snapshot persistence. Upsert/decrement methods used by live scanner for
 * individual reaction events.
 */
public interface MessageReactionDao {

    @SqlBatch("""
            INSERT INTO message_reactions (
                message_event_id, emoji_name, emoji_ext_id, count, burst_count
            ) VALUES (
                :messageEventId, :emojiName, :emojiExtId, :count, :burstCount
            )
            ON CONFLICT (message_event_id, emoji_name, COALESCE(emoji_ext_id, 0))
            DO UPDATE SET count = EXCLUDED.count, burst_count = EXCLUDED.burst_count
            """)
    void insertBatch(@BindMethods List<MessageReaction> reactions);

    /**
     * Atomically increment a single reaction's count. Used by REACTION_ADD live
     * events. Inserts a new row if this emoji hasn't been seen on the message
     * yet.
     */
    @SqlUpdate("""
            INSERT INTO message_reactions (
                message_event_id, emoji_name, emoji_ext_id, count, burst_count
            ) VALUES (
                :messageEventId, :emojiName, :emojiExtId, 1, 0
            )
            ON CONFLICT (message_event_id, emoji_name, COALESCE(emoji_ext_id, 0))
            DO UPDATE SET count = message_reactions.count + 1
            """)
    void incrementCount(
            @Bind("messageEventId") long messageEventId,
            @Bind("emojiName") String emojiName,
            @Bind("emojiExtId") Long emojiExtId);

    /**
     * Atomically decrement a single reaction's count. Used by REACTION_REMOVE
     * live events. Floors at 0 to avoid negative counts from event ordering
     * quirks.
     */
    @SqlUpdate("""
            UPDATE message_reactions
            SET count = MAX(count - 1, 0)
            WHERE message_event_id = :messageEventId
                AND emoji_name = :emojiName
                AND COALESCE(emoji_ext_id, 0) = COALESCE(:emojiExtId, 0)
            """)
    void decrementCount(
            @Bind("messageEventId") long messageEventId,
            @Bind("emojiName") String emojiName,
            @Bind("emojiExtId") Long emojiExtId);

    @SqlUpdate("DELETE FROM message_reactions WHERE message_event_id = :messageEventId")
    void deleteByMessageEventId(@Bind("messageEventId") long messageEventId);
}
