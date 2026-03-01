package edu.franklin.acm.synapse.activity.thread;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface ThreadTagDao {

    @SqlUpdate("""
            INSERT INTO thread_tags (thread_id, forum_tag_id)
            VALUES (:threadId, :forumTagId)
            ON CONFLICT (thread_id, forum_tag_id) DO NOTHING
            """)
    void insert(@Bind("threadId") long threadId, @Bind("forumTagId") long forumTagId);

    @SqlUpdate("DELETE FROM thread_tags WHERE thread_id = :threadId")
    void deleteByThreadId(@Bind("threadId") long threadId);
}
