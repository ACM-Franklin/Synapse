package edu.franklin.acm.synapse.rules.engine;

import java.nio.file.Path;
import java.util.List;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerDao;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerEntry;
import edu.franklin.acm.synapse.activity.rules.RuleDao;
import edu.franklin.acm.synapse.activity.rules.RuleEvaluationDao;
import edu.franklin.acm.synapse.activity.rules.RulePredicateDao;
import edu.franklin.acm.synapse.test.JdbiTestSupport;

class RuleEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void currencyOutcomeWritesLedgerAndDoesNotDoubleAwardSameEvent() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 7001L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 8001L);
        long eventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long ruleId = JdbiTestSupport.insertRule(jdbi, "rule-engine-award", 0);
        JdbiTestSupport.insertOutcome(jdbi, ruleId, 5, 2);
        RuleContext context = RuleContext.forMemberEvent(
                "MESSAGE_CREATE", eventId, memberId, 7001L, false, null, 0, 0);
        RuleEngine engine = ruleEngine(jdbi);

        engine.evaluate(context);
        engine.evaluate(context);

        MemberDao memberDao = JdbiTestSupport.dao(jdbi, MemberDao.class);
        List<RewardLedgerEntry> ledger = JdbiTestSupport.dao(jdbi, RewardLedgerDao.class).findRecentByMember(memberId, 10);

        assertAll(
                () -> assertEquals(5, memberDao.findPCurrency(memberId)),
                () -> assertEquals(2, memberDao.findSCurrency(memberId)),
                () -> assertEquals(2, ledger.size()),
                () -> assertEquals(1, JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class).countByRuleAndEvent(ruleId, eventId)));
    }

    @Test
    void cooldownSuppressesSecondEventForSameMember() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 7002L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 8002L);
        long firstEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long secondEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long ruleId = JdbiTestSupport.insertRule(jdbi, "rule-engine-cooldown", 3600);
        JdbiTestSupport.insertOutcome(jdbi, ruleId, 1, null);
        RuleEngine engine = ruleEngine(jdbi);

        engine.evaluate(RuleContext.forMemberEvent(
                "MESSAGE_CREATE", firstEventId, memberId, 7002L, false, null, 0, 0));
        engine.evaluate(RuleContext.forMemberEvent(
                "MESSAGE_CREATE", secondEventId, memberId, 7002L, false, null, 0, 0));

        assertAll(
                () -> assertEquals(1, JdbiTestSupport.dao(jdbi, MemberDao.class).findPCurrency(memberId)),
                () -> assertEquals(1, JdbiTestSupport.dao(jdbi, RewardLedgerDao.class).findRecentByMember(memberId, 10).size()),
                () -> assertEquals(1, JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class).countByRuleAndEvent(ruleId, firstEventId)),
                () -> assertEquals(0, JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class).countByRuleAndEvent(ruleId, secondEventId)));
    }

    private static RuleEngine ruleEngine(Jdbi jdbi) {
        RuleEngine engine = new RuleEngine();
        engine.ruleDao = JdbiTestSupport.dao(jdbi, RuleDao.class);
        engine.rulePredicateDao = JdbiTestSupport.dao(jdbi, RulePredicateDao.class);
        engine.ruleEvaluationDao = JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class);
        engine.jdbi = jdbi;
        return engine;
    }
}
