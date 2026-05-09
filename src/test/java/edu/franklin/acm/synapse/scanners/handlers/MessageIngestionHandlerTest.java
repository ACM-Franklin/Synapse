package edu.franklin.acm.synapse.scanners.handlers;

import java.nio.file.Path;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.message.MessageEventDao;
import edu.franklin.acm.synapse.activity.message.MessageReactionDao;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerDao;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerEntry;
import edu.franklin.acm.synapse.activity.rules.RuleEvaluationDao;
import edu.franklin.acm.synapse.test.JdbiTestSupport;

class MessageIngestionHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteTombstonesMessageAndReversesCurrencyAwardsOnce() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 9001L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 9002L);
        long messageEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long messageExtId = 9003L;
        JdbiTestSupport.dao(jdbi, MessageEventDao.class).upsert(
                JdbiTestSupport.messageEvent(messageEventId, messageExtId, "delete reward", 0, false, 0));

        long ruleId = JdbiTestSupport.insertRule(jdbi, "delete-reversal", 0);
        long outcomeId = JdbiTestSupport.insertOutcome(jdbi, ruleId, 10, null);
        long evaluationId = JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class).insert(ruleId, messageEventId, memberId);
        JdbiTestSupport.dao(jdbi, MemberDao.class).incrementPCurrency(memberId, 10);
        long awardId = JdbiTestSupport.dao(jdbi, RewardLedgerDao.class).insert(new RewardLedgerEntry(
                0L,
                evaluationId,
                outcomeId,
                ruleId,
                messageEventId,
                memberId,
                "PRIMARY",
                10,
                "AWARD",
                null,
                null));
        MessageIngestionHandler handler = new MessageIngestionHandler();
        handler.jdbi = jdbi;

        handler.handleDelete(messageExtId);
        handler.handleDelete(messageExtId);

        int isDeleted = JdbiTestSupport.queryInt(jdbi, """
                SELECT is_deleted FROM messages WHERE ext_id = :messageExtId
                """, "messageExtId", messageExtId);
        int deleteEvents = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM events WHERE event_type = 'MESSAGE_DELETE'
                """);
        int reversalRows = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger
                WHERE transaction_type = 'REVERSAL'
                  AND reverses_reward_ledger_id = :awardId
                  AND amount = -10
                """, "awardId", awardId);

        assertAll(
                () -> assertEquals(1, isDeleted),
                () -> assertEquals(1, deleteEvents),
                () -> assertEquals(1, reversalRows),
                () -> assertEquals(0, JdbiTestSupport.dao(jdbi, MemberDao.class).findPCurrency(memberId)));
    }

    @Test
    void reactionRemoveAllClearsStoredReactionSnapshot() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long messageExtId = 9101L;
        long messageId = insertMessage(jdbi, messageExtId);
        addReactions(jdbi, messageId, "spark", null, 2);
        addReactions(jdbi, messageId, "wave", null, 1);
        MessageIngestionHandler handler = reactionHandler(jdbi);

        handler.handleReactionRemoveAll(messageExtId);

        int reactionRows = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM message_reactions WHERE message_id = :messageId
                """, "messageId", messageId);
        int aggregateCount = JdbiTestSupport.queryInt(jdbi, """
                SELECT reaction_count FROM messages WHERE id = :messageId
                """, "messageId", messageId);

        assertAll(
                () -> assertEquals(0, reactionRows),
                () -> assertEquals(0, aggregateCount));
    }

    @Test
    void reactionRemoveEmojiClearsOnlyThatEmojiAndPreservesOtherCounts() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long messageExtId = 9201L;
        long messageId = insertMessage(jdbi, messageExtId);
        addReactions(jdbi, messageId, "spark", null, 2);
        addReactions(jdbi, messageId, "wave", null, 1);
        MessageIngestionHandler handler = reactionHandler(jdbi);

        handler.handleReactionRemoveEmoji(messageExtId, "spark", null);

        int sparkRows = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM message_reactions
                WHERE message_id = :messageId AND emoji_name = 'spark'
                """, "messageId", messageId);
        int waveCount = JdbiTestSupport.queryInt(jdbi, """
                SELECT count FROM message_reactions
                WHERE message_id = :messageId AND emoji_name = 'wave'
                """, "messageId", messageId);
        int aggregateCount = JdbiTestSupport.queryInt(jdbi, """
                SELECT reaction_count FROM messages WHERE id = :messageId
                """, "messageId", messageId);

        assertAll(
                () -> assertEquals(0, sparkRows),
                () -> assertEquals(1, waveCount),
                () -> assertEquals(1, aggregateCount));
    }

    private static long insertMessage(Jdbi jdbi, long extId) {
        long memberId = JdbiTestSupport.insertMember(jdbi, extId + 1000L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, extId + 2000L);
        long eventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        return JdbiTestSupport.dao(jdbi, MessageEventDao.class).upsert(
                JdbiTestSupport.messageEvent(eventId, extId, "reaction target", 0, false, 0));
    }

    private static void addReactions(Jdbi jdbi, long messageId, String emojiName, Long emojiExtId, int count) {
        MessageReactionDao reactionDao = JdbiTestSupport.dao(jdbi, MessageReactionDao.class);
        MessageEventDao messageDao = JdbiTestSupport.dao(jdbi, MessageEventDao.class);
        for (int i = 0; i < count; i++) {
            reactionDao.incrementCount(messageId, emojiName, emojiExtId);
            messageDao.incrementReactionCount(messageId);
        }
    }

    private static MessageIngestionHandler reactionHandler(Jdbi jdbi) {
        MessageIngestionHandler handler = new MessageIngestionHandler();
        handler.messageEventDao = JdbiTestSupport.dao(jdbi, MessageEventDao.class);
        handler.messageReactionDao = JdbiTestSupport.dao(jdbi, MessageReactionDao.class);
        return handler;
    }
}
