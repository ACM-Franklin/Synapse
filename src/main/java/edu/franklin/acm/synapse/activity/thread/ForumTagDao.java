package edu.franklin.acm.synapse.activity.thread;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface ForumTagDao {

    @SqlQuery("""
            INSERT INTO forum_tags (ext_id, channel_id, name, emoji_name, emoji_ext_id, is_moderated, created_at)
            VALUES (:extId, :channelId, :name, :emojiName, :emojiExtId, :isModerated,
                    COALESCE(:createdAt, CURRENT_TIMESTAMP))
            ON CONFLICT (ext_id) DO UPDATE SET
                name         = :name,
                emoji_name   = :emojiName,
                emoji_ext_id = :emojiExtId,
                is_moderated = :isModerated,
                updated_at   = CURRENT_TIMESTAMP
            RETURNING id
            """)
    long upsert(
            @Bind("extId") long extId,
            @Bind("channelId") long channelId,
            @Bind("name") String name,
            @Bind("emojiName") String emojiName,
            @Bind("emojiExtId") Long emojiExtId,
            @Bind("isModerated") boolean isModerated,
            @Bind("createdAt") String createdAt);

    @SqlQuery("SELECT id FROM forum_tags WHERE ext_id = :extId")
    Long findIdByExtId(@Bind("extId") long extId);
}
