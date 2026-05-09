package edu.franklin.acm.synapse.activity.message;

import java.nio.file.Path;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.test.JdbiTestSupport;

class MessageReactionDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void reactionCountsIncrementUpdateAndFloorAtZero() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long messageId = insertMessage(jdbi, 4001L);
        MessageReactionDao reactionDao = JdbiTestSupport.dao(jdbi, MessageReactionDao.class);
        MessageEventDao messageDao = JdbiTestSupport.dao(jdbi, MessageEventDao.class);

        reactionDao.incrementCount(messageId, "spark", null);
        reactionDao.incrementCount(messageId, "spark", null);
        reactionDao.decrementCount(messageId, "spark", null);
        reactionDao.decrementCount(messageId, "spark", null);
        reactionDao.decrementCount(messageId, "spark", null);

        messageDao.incrementReactionCount(messageId);
        messageDao.decrementReactionCount(messageId);
        messageDao.decrementReactionCount(messageId);

        int perEmojiCount = JdbiTestSupport.queryInt(jdbi, """
                SELECT count FROM message_reactions
                WHERE message_id = :messageId AND emoji_name = 'spark' AND emoji_ext_id IS NULL
            """, "messageId", messageId);
        int aggregateCount = JdbiTestSupport.queryInt(jdbi, """
                SELECT reaction_count FROM messages WHERE id = :messageId
            """, "messageId", messageId);
        int rowCount = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM message_reactions
                WHERE message_id = :messageId AND emoji_name = 'spark'
            """, "messageId", messageId);

        assertAll(
                () -> assertEquals(0, perEmojiCount),
                () -> assertEquals(0, aggregateCount),
                () -> assertEquals(1, rowCount));
    }

    private static long insertMessage(Jdbi jdbi, long extId) {
        long memberId = JdbiTestSupport.insertMember(jdbi, extId + 1000L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, extId + 2000L);
        long eventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        return JdbiTestSupport.dao(jdbi, MessageEventDao.class).upsert(
                JdbiTestSupport.messageEvent(eventId, extId, "reaction target", 0, false, 0));
    }
}
