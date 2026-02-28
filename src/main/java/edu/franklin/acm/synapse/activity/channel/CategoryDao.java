package edu.franklin.acm.synapse.activity.channel;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface CategoryDao {

    @SqlQuery("""
            INSERT INTO categories (ext_id, name)
            VALUES (:extId, :name)
            ON CONFLICT (ext_id) DO UPDATE SET name = :name
            RETURNING id
            """)
    long upsert(@Bind("extId") long extId, @Bind("name") String name);
}
