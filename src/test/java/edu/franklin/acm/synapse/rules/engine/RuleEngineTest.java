package edu.franklin.acm.synapse.rules.engine;

import java.nio.file.Path;
import java.util.List;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.message.MessageEvent;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerDao;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerEntry;
import edu.franklin.acm.synapse.activity.rules.RuleDao;
import edu.franklin.acm.synapse.activity.rules.RuleEvaluationDao;
import edu.franklin.acm.synapse.activity.rules.RulePredicate;
import edu.franklin.acm.synapse.activity.rules.RulePredicateDao;
import edu.franklin.acm.synapse.rules.engine.predicates.NumericThresholdEvaluator;
import edu.franklin.acm.synapse.test.JdbiTestSupport;
import jakarta.enterprise.inject.Instance;

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

    @Test
    void messageAwardCarriesStableMessageSubjectIdentity() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 7003L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 8003L);
        long eventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long messageExtId = 9003L;
        long ruleId = JdbiTestSupport.insertRule(jdbi, "rule-engine-message-subject", 0);
        JdbiTestSupport.insertOutcome(jdbi, ruleId, 5, null);
        RuleEngine engine = ruleEngine(jdbi);
        MessageEvent messageEvent = JdbiTestSupport.messageEvent(eventId, messageExtId, "subject tagged", 0, false, 0);

        engine.evaluate(RuleContext.forMessage(
                eventId,
                memberId,
                channelId,
                messageEvent,
                7003L,
                false,
                null,
                0,
                0,
                8003L,
                "TEXT",
                null,
                null,
                null));

        RewardLedgerEntry ledgerEntry = JdbiTestSupport.dao(jdbi, RewardLedgerDao.class)
                .findRecentByMember(memberId, 10)
                .get(0);

        assertAll(
                () -> assertEquals("MESSAGE", ledgerEntry.subjectType()),
                () -> assertEquals(messageExtId, ledgerEntry.subjectExtId()));
    }

    @Test
        void replacementEvaluationPreservesMessageSubjectAwardsDespiteCooldownWhenResultIsUnchanged() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 7004L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 8004L);
        long createEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long updateEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_UPDATE");
        long messageExtId = 9004L;
        long ruleId = JdbiTestSupport.insertRule(jdbi, "rule-engine-message-replacement", 3600);
        JdbiTestSupport.insertOutcome(jdbi, ruleId, 10, null);
        RuleEngine engine = ruleEngine(jdbi);
        MessageEvent createMessage = JdbiTestSupport.messageEvent(createEventId, messageExtId, "reward me", 0, false, 0);
        MessageEvent updatedMessage = JdbiTestSupport.messageEvent(updateEventId, messageExtId, "reward me again", 0, false, 0);

        engine.evaluate(RuleContext.forMessage(
                createEventId,
                memberId,
                channelId,
                createMessage,
                7004L,
                false,
                null,
                0,
                0,
                8004L,
                "TEXT",
                null,
                null,
                null));

        RewardLedgerDao rewardLedgerDao = JdbiTestSupport.dao(jdbi, RewardLedgerDao.class);
        RewardLedgerEntry originalAward = rewardLedgerDao.findRecentByMember(memberId, 10).get(0);

        engine.onEvaluationRequest(new RuleEvaluationRequest(
                RuleContext.forMessage(
                        updateEventId,
                        memberId,
                        channelId,
                        updatedMessage,
                        7004L,
                        false,
                        null,
                        10,
                        0,
                        8004L,
                        "TEXT",
                        null,
                        null,
                        null),
                true));

        int reversalRows = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger
                WHERE transaction_type = 'REVERSAL'
                  AND reverses_reward_ledger_id = :awardId
                """, "awardId", originalAward.id());
        int updateEvaluations = JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class)
                .countByRuleAndEvent(ruleId, updateEventId);
        int updateAwards = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger
                WHERE event_id = :eventId
                  AND transaction_type = 'AWARD'
                """, "eventId", updateEventId);

        assertAll(
                () -> assertEquals(10, JdbiTestSupport.dao(jdbi, MemberDao.class).findPCurrency(memberId)),
                () -> assertEquals(0, reversalRows),
                () -> assertEquals(1, updateEvaluations),
                () -> assertEquals(0, updateAwards));
    }

    @Test
    void replacementEvaluationReversesAwardsWhenTheEditedMessageNoLongerMatches() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 7005L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 8005L);
        long createEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long updateEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_UPDATE");
        long messageExtId = 9005L;
        long ruleId = JdbiTestSupport.insertRule(jdbi, "rule-engine-message-reversal", 0);
        JdbiTestSupport.insertOutcome(jdbi, ruleId, 10, null);
        JdbiTestSupport.dao(jdbi, RulePredicateDao.class).insertBatch(List.of(
                new RulePredicate(0L, ruleId, "MIN_CONTENT_LENGTH", "{\"threshold\": 5}", 0)));
        RuleEngine engine = ruleEngine(jdbi, new NumericThresholdEvaluator());

        engine.evaluate(RuleContext.forMessage(
                createEventId,
                memberId,
                channelId,
                JdbiTestSupport.messageEvent(createEventId, messageExtId, "rewarded", 0, false, 0),
                7005L,
                false,
                null,
                0,
                0,
                8005L,
                "TEXT",
                null,
                null,
                null));

        RewardLedgerEntry originalAward = JdbiTestSupport.dao(jdbi, RewardLedgerDao.class)
                .findRecentByMember(memberId, 10)
                .get(0);

        engine.onEvaluationRequest(new RuleEvaluationRequest(
                RuleContext.forMessage(
                        updateEventId,
                        memberId,
                        channelId,
                        JdbiTestSupport.messageEvent(updateEventId, messageExtId, "no", 0, false, 0),
                        7005L,
                        false,
                        null,
                        10,
                        0,
                        8005L,
                        "TEXT",
                        null,
                        null,
                        null),
                true));

        int reversalRows = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger
                WHERE transaction_type = 'REVERSAL'
                  AND reverses_reward_ledger_id = :awardId
                """, "awardId", originalAward.id());
        int updateAwards = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger
                WHERE event_id = :eventId
                  AND transaction_type = 'AWARD'
                """, "eventId", updateEventId);

        assertAll(
                () -> assertEquals(0, JdbiTestSupport.dao(jdbi, MemberDao.class).findPCurrency(memberId)),
                () -> assertEquals(1, reversalRows),
                () -> assertEquals(0, updateAwards));
    }

    @Test
    void replacementEvaluationUsesPreSubjectBalancesForBalancePredicates() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 7006L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 8006L);
        long createEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long updateEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_UPDATE");
        long messageExtId = 9006L;
        long ruleId = JdbiTestSupport.insertRule(jdbi, "rule-engine-balance-normalization", 0);
        JdbiTestSupport.insertOutcome(jdbi, ruleId, 10, null);
        JdbiTestSupport.dao(jdbi, RulePredicateDao.class).insertBatch(List.of(
                new RulePredicate(0L, ruleId, "MEMBER_P_CURRENCY_MAX", "{\"threshold\": 0}", 0)));
        RuleEngine engine = ruleEngine(jdbi, new NumericThresholdEvaluator());

        engine.evaluate(RuleContext.forMessage(
                createEventId,
                memberId,
                channelId,
                JdbiTestSupport.messageEvent(createEventId, messageExtId, "rewarded", 0, false, 0),
                7006L,
                false,
                null,
                0,
                0,
                8006L,
                "TEXT",
                null,
                null,
                null));

        RewardLedgerEntry originalAward = JdbiTestSupport.dao(jdbi, RewardLedgerDao.class)
                .findRecentByMember(memberId, 10)
                .get(0);

        engine.onEvaluationRequest(new RuleEvaluationRequest(
                RuleContext.forMessage(
                        updateEventId,
                        memberId,
                        channelId,
                        JdbiTestSupport.messageEvent(updateEventId, messageExtId, "rewarded again", 0, false, 0),
                        7006L,
                        false,
                        null,
                        10,
                        0,
                        8006L,
                        "TEXT",
                        null,
                        null,
                        null),
                true));

        int reversalRows = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger
                WHERE transaction_type = 'REVERSAL'
                  AND reverses_reward_ledger_id = :awardId
                """, "awardId", originalAward.id());
        int updateAwards = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger
                WHERE event_id = :eventId
                  AND transaction_type = 'AWARD'
                """, "eventId", updateEventId);
        int updateEvaluations = JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class)
                .countByRuleAndEvent(ruleId, updateEventId);

        assertAll(
                () -> assertEquals(10, JdbiTestSupport.dao(jdbi, MemberDao.class).findPCurrency(memberId)),
                () -> assertEquals(0, reversalRows),
                () -> assertEquals(0, updateAwards),
                () -> assertEquals(1, updateEvaluations));
    }

    @Test
    void replacementEvaluationDoesNotChurnLedgerWhenTheResultIsUnchanged() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 7007L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 8007L);
        long createEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long updateEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_UPDATE");
        long messageExtId = 9007L;
        long ruleId = JdbiTestSupport.insertRule(jdbi, "rule-engine-no-churn", 0);
        JdbiTestSupport.insertOutcome(jdbi, ruleId, 10, null);
        RuleEngine engine = ruleEngine(jdbi);

        engine.evaluate(RuleContext.forMessage(
                createEventId,
                memberId,
                channelId,
                JdbiTestSupport.messageEvent(createEventId, messageExtId, "same result", 0, false, 0),
                7007L,
                false,
                null,
                0,
                0,
                8007L,
                "TEXT",
                null,
                null,
                null));

        engine.onEvaluationRequest(new RuleEvaluationRequest(
                RuleContext.forMessage(
                        updateEventId,
                        memberId,
                        channelId,
                        JdbiTestSupport.messageEvent(updateEventId, messageExtId, "same result", 0, false, 0),
                        7007L,
                        false,
                        null,
                        10,
                        0,
                        8007L,
                        "TEXT",
                        null,
                        null,
                        null),
                true));

        int reversalRows = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger WHERE transaction_type = 'REVERSAL'
                """);
        int updateAwards = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger
                WHERE event_id = :eventId AND transaction_type = 'AWARD'
                """, "eventId", updateEventId);

        assertAll(
                () -> assertEquals(10, JdbiTestSupport.dao(jdbi, MemberDao.class).findPCurrency(memberId)),
                () -> assertEquals(0, reversalRows),
                () -> assertEquals(0, updateAwards),
                () -> assertEquals(1, JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class).countByRuleAndEvent(ruleId, updateEventId)));
    }

    private static RuleEngine ruleEngine(Jdbi jdbi) {
        return ruleEngine(jdbi, new PredicateEvaluator[0]);
    }

    private static RuleEngine ruleEngine(Jdbi jdbi, PredicateEvaluator... evaluators) {
        RuleEngine engine = new RuleEngine();
        engine.ruleDao = JdbiTestSupport.dao(jdbi, RuleDao.class);
        engine.rulePredicateDao = JdbiTestSupport.dao(jdbi, RulePredicateDao.class);
        engine.ruleEvaluationDao = JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class);
        engine.jdbi = jdbi;
        @SuppressWarnings("unchecked")
        Instance<PredicateEvaluator> evaluatorInstance = mock(Instance.class);
                when(evaluatorInstance.iterator()).thenAnswer(invocation -> List.of(evaluators).iterator());
        engine.evaluatorBeans = evaluatorInstance;
        return engine;
    }
}
