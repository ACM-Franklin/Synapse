package edu.franklin.acm.synapse.activity.rules;

import java.nio.file.Path;
import java.util.List;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.test.JdbiTestSupport;

class RewardLedgerDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void unreversedAwardLookupExcludesAwardsWithReversals() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 5001L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 6001L);
        long eventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long ruleId = JdbiTestSupport.insertRule(jdbi, "ledger-test", 0);
        long outcomeId = JdbiTestSupport.insertOutcome(jdbi, ruleId, 10, null);
        long evaluationId = JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class).insert(ruleId, eventId, memberId);
        RewardLedgerDao dao = JdbiTestSupport.dao(jdbi, RewardLedgerDao.class);

        long awardId = dao.insert(new RewardLedgerEntry(
                0L, evaluationId, outcomeId, ruleId, eventId, memberId,
                "PRIMARY", 10, "AWARD", null, null));
        List<RewardLedgerEntry> unreversedAwards = dao.findUnreversedAwardsByEventId(eventId);

        long deleteEventId = JdbiTestSupport.dao(jdbi, EventDao.class).insert(new edu.franklin.acm.synapse.activity.Event(
                0L, memberId, channelId, "MESSAGE_DELETE", "2026-01-01T00:01:00"));
        dao.insert(new RewardLedgerEntry(
                0L, evaluationId, outcomeId, ruleId, deleteEventId, memberId,
                "PRIMARY", -10, "REVERSAL", awardId, null));

        assertAll(
                () -> assertEquals(1, unreversedAwards.size()),
                () -> assertEquals(awardId, unreversedAwards.get(0).id()),
                () -> assertTrue(dao.findUnreversedAwardsByEventId(eventId).isEmpty()));
    }
}
