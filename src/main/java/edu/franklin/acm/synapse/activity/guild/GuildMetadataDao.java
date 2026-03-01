package edu.franklin.acm.synapse.activity.guild;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Single-row table holding metadata about the guild this instance manages.
 */
public interface GuildMetadataDao {

    @SqlUpdate("""
            INSERT INTO guild_metadata (id, ext_id, name, created_at)
            VALUES (1, :extId, :name, COALESCE(:createdAt, CURRENT_TIMESTAMP))
            ON CONFLICT (id) DO UPDATE SET
                ext_id = :extId,
                name = :name,
                updated_at = CURRENT_TIMESTAMP
            """)
    void upsert(@Bind("extId") long extId, @Bind("name") String name,
                @Bind("createdAt") String createdAt);

    @SqlQuery("SELECT ext_id FROM guild_metadata WHERE id = 1")
    Long getExtId();

    @SqlQuery("SELECT name FROM guild_metadata WHERE id = 1")
    String getName();
}
