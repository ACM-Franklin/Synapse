package edu.franklin.acm.synapse.activity.message;

import java.nio.file.Path;
import java.util.Map;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.test.JdbiTestSupport;

class MessageEventDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void upsertPreservesImmutableFieldsAndUpdatesMutableFields() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 1001L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 2001L);
        long originalEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long laterEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        MessageEventDao dao = JdbiTestSupport.dao(jdbi, MessageEventDao.class);

        long messageId = dao.upsert(JdbiTestSupport.messageEvent(
                originalEventId, 3001L, "first version", 0, false, 1));
        long updatedMessageId = dao.upsert(JdbiTestSupport.messageEvent(
                laterEventId, 3001L, "edited version", 99, true, 4));

        Map<String, Object> row = JdbiTestSupport.queryRow(jdbi, """
                SELECT id, event_id, type, author_is_bot, content, content_length, reaction_count
                FROM messages
                WHERE ext_id = :extId
            """, "extId", 3001L);

        assertAll(
                () -> assertEquals(messageId, updatedMessageId),
                () -> assertEquals(originalEventId, longValue(row, "event_id")),
                () -> assertEquals(0, intValue(row, "type")),
                () -> assertEquals(0, intValue(row, "author_is_bot")),
                () -> assertEquals("edited version", row.get("content")),
                () -> assertEquals("edited version".length(), intValue(row, "content_length")),
                () -> assertEquals(4, intValue(row, "reaction_count")));
    }

    @Test
    void deletionTargetAndTombstoneUseOriginalMessageEvent() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 1002L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 2002L);
        long eventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        MessageEventDao dao = JdbiTestSupport.dao(jdbi, MessageEventDao.class);
        long messageId = dao.upsert(JdbiTestSupport.messageEvent(
                eventId, 3002L, "delete me", 0, false, 0));

        MessageDeletionTarget target = dao.findDeletionTargetByExtId(3002L);
        dao.markDeleted(3002L);
        Map<String, Object> row = JdbiTestSupport.queryRow(jdbi, """
                SELECT is_deleted, deleted_at FROM messages WHERE ext_id = :extId
            """, "extId", 3002L);

        assertAll(
                () -> assertEquals(messageId, target.messageId()),
                () -> assertEquals(eventId, target.eventId()),
                () -> assertEquals(memberId, target.memberId()),
                () -> assertEquals(channelId, target.channelId()),
                () -> assertEquals(1, intValue(row, "is_deleted")),
                () -> assertNotNull(row.get("deleted_at")),
                () -> assertNull(dao.findDeletionTargetByExtId(3002L)));
    }

    private static int intValue(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }

    private static long longValue(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
