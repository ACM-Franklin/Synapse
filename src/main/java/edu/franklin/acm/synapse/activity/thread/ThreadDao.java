package edu.franklin.acm.synapse.activity.thread;

import java.util.Collection;
import java.util.List;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface ThreadDao {

    @SqlQuery("""
            INSERT INTO threads (ext_id, channel_id, owner_ext_id, name, type,
                is_archived, is_locked, is_pinned, message_count,
                slowmode, auto_archive_duration, is_active, created_at)
            VALUES (:extId, :channelId, :ownerExtId, :name, :type,
                :isArchived, :isLocked, :isPinned, :messageCount,
                :slowmode, :autoArchiveDuration, 1,
                COALESCE(:createdAt, CURRENT_TIMESTAMP))
            ON CONFLICT (ext_id) DO UPDATE SET
                name                  = :name,
                is_archived           = :isArchived,
                is_locked             = :isLocked,
                is_pinned             = :isPinned,
                message_count         = :messageCount,
                slowmode              = :slowmode,
                auto_archive_duration = :autoArchiveDuration,
                is_active             = 1,
                updated_at            = CURRENT_TIMESTAMP
            RETURNING id
            """)
    long upsert(
            @Bind("extId") long extId,
            @Bind("channelId") long channelId,
            @Bind("ownerExtId") Long ownerExtId,
            @Bind("name") String name,
            @Bind("type") String type,
            @Bind("isArchived") boolean isArchived,
            @Bind("isLocked") boolean isLocked,
            @Bind("isPinned") boolean isPinned,
            @Bind("messageCount") int messageCount,
            @Bind("slowmode") int slowmode,
            @Bind("autoArchiveDuration") int autoArchiveDuration,
            @Bind("createdAt") String createdAt);

    @SqlQuery("SELECT id FROM threads WHERE ext_id = :extId")
    Long findIdByExtId(@Bind("extId") long extId);

    @SqlQuery("SELECT ext_id FROM threads WHERE is_active = 1")
    List<Long> findAllActiveExtIds();

    @SqlUpdate("UPDATE threads SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE ext_id IN (<extIds>)")
    void deactivateByExtIds(@BindList("extIds") Collection<Long> extIds);

    @SqlUpdate("UPDATE threads SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE ext_id = :extId")
    void markInactive(@Bind("extId") long extId);
}
