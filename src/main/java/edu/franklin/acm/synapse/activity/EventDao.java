package edu.franklin.acm.synapse.activity;

import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Writes to and reads from the lean Event Lake parent table.
 * No JSON blob. Child tables carry type-specific detail.
 */
@RegisterConstructorMapper(Event.class)
public interface EventDao {

    @SqlUpdate("""
            INSERT INTO events (
                member_id,
                channel_id,
                event_type
            ) VALUES (
                :memberId,
                :channelId,
                :eventType
            )
            """)
    @GetGeneratedKeys
    long insert(@BindMethods Event event);

    @SqlQuery("""
            SELECT
                id,
                member_id,
                channel_id,
                event_type,
                created_at
            FROM events
            WHERE member_id = :memberId
            ORDER BY id DESC
            LIMIT :limit
            """)
    List<Event> findRecentByMember(@Bind("memberId") long memberId, @Bind("limit") int limit);

    @SqlQuery("""
            SELECT COUNT(*) FROM events
            WHERE member_id = :memberId AND event_type = :eventType
            """)
    int countByMemberAndType(@Bind("memberId") long memberId, @Bind("eventType") String eventType);
}
