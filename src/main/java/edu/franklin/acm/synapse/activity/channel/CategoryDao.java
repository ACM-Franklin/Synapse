package edu.franklin.acm.synapse.activity.channel;

import java.util.Collection;
import java.util.List;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface CategoryDao {

    @SqlQuery("""
            INSERT INTO categories (ext_id, name, is_active, created_at)
            VALUES (:extId, :name, 1, COALESCE(:createdAt, CURRENT_TIMESTAMP))
            ON CONFLICT (ext_id) DO UPDATE SET
                name       = :name,
                is_active  = 1,
                updated_at = CURRENT_TIMESTAMP
            RETURNING id
            """)
    long upsert(@Bind("extId") long extId, @Bind("name") String name,
                @Bind("createdAt") String createdAt);

    @SqlQuery("SELECT ext_id FROM categories WHERE is_active = 1")
    List<Long> findAllActiveExtIds();

    @SqlUpdate("UPDATE categories SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE ext_id IN (<extIds>)")
    void deactivateByExtIds(@BindList("extIds") Collection<Long> extIds);

    @SqlUpdate("UPDATE categories SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE ext_id = :extId")
    void markInactive(@Bind("extId") long extId);
}
