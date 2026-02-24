package edu.franklin.acm.synapse.activity.voice;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Manages voice session rows. Sessions are opened on voice join and closed
 * on voice leave/move. Orphaned sessions (left_at IS NULL after a restart)
 * are closed by the startup reconciliation pass.
 */
public interface VoiceSessionDao {

    /**
     * Open a new voice session. Returns the generated row id.
     */
    @SqlUpdate("""
            INSERT INTO voice_sessions (event_id, member_id, channel_id, joined_at)
            VALUES (:eventId, :memberId, :channelId, :joinedAt)
            """)
    @GetGeneratedKeys
    long open(
            @Bind("eventId") long eventId,
            @Bind("memberId") long memberId,
            @Bind("channelId") long channelId,
            @Bind("joinedAt") String joinedAt);

    /**
     * Close a voice session for a member in a specific channel.
     * Computes duration_secs from the difference between left_at and joined_at.
     * Only closes the most recent open session (left_at IS NULL).
     */
    @SqlUpdate("""
            UPDATE voice_sessions
            SET left_at = :leftAt,
                duration_secs = (julianday(:leftAt) - julianday(joined_at)) * 86400.0
            WHERE member_id = :memberId
              AND channel_id = :channelId
              AND left_at IS NULL
            """)
    void close(
            @Bind("memberId") long memberId,
            @Bind("channelId") long channelId,
            @Bind("leftAt") String leftAt);

    /**
     * Close all orphaned sessions for a specific member (any channel).
     * Used when a member disconnects entirely or during startup reconciliation.
     */
    @SqlUpdate("""
            UPDATE voice_sessions
            SET left_at = :leftAt,
                duration_secs = (julianday(:leftAt) - julianday(joined_at)) * 86400.0
            WHERE member_id = :memberId
              AND left_at IS NULL
            """)
    void closeAllForMember(
            @Bind("memberId") long memberId,
            @Bind("leftAt") String leftAt);

    /**
     * Close ALL orphaned sessions. Used during startup reconciliation
     * before re-opening sessions for members currently in voice.
     */
    @SqlUpdate("""
            UPDATE voice_sessions
            SET left_at = :leftAt,
                duration_secs = (julianday(:leftAt) - julianday(joined_at)) * 86400.0
            WHERE left_at IS NULL
            """)
    void closeAllOrphaned(@Bind("leftAt") String leftAt);

    /**
     * Check if a member has an open session in a specific channel.
     */
    @SqlQuery("""
            SELECT COUNT(*) FROM voice_sessions
            WHERE member_id = :memberId
              AND channel_id = :channelId
              AND left_at IS NULL
            """)
    int countOpen(
            @Bind("memberId") long memberId,
            @Bind("channelId") long channelId);
}
