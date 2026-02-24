package edu.franklin.acm.synapse.activity.channel;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface ChannelDao {

    @SqlQuery("""
            INSERT INTO channels (ext_id, name, category_id)
            VALUES (:extId, :name, :categoryId)
            ON CONFLICT (ext_id) DO UPDATE SET name = :name
            RETURNING id
            """)
    long upsert(@Bind("extId") long extId, @Bind("name") String name, @Bind("categoryId") Long categoryId);

    @SqlQuery("SELECT id FROM channels WHERE ext_id = :extId")
    Long findIdByExtId(@Bind("extId") long extId);
}
