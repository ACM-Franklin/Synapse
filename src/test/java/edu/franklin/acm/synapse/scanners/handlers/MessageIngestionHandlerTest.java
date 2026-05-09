package edu.franklin.acm.synapse.scanners.handlers;

import java.nio.file.Path;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.message.MessageEventDao;
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
}
