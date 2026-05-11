package edu.franklin.acm.synapse.rules.engine;

import java.nio.file.Path;
import java.util.List;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.message.MessageAttachmentDao;
import edu.franklin.acm.synapse.activity.message.MessageEventDao;
import edu.franklin.acm.synapse.activity.rules.RuleDao;
import edu.franklin.acm.synapse.activity.rules.RuleEvaluationDao;
import edu.franklin.acm.synapse.activity.rules.RulePredicateDao;
import edu.franklin.acm.synapse.test.JdbiTestSupport;
import jakarta.enterprise.inject.Instance;

class MessageRewardReplayServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void replayMessageRecomputesCurrentSubjectStateIdempotentlyWithoutLedgerChurn() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 7101L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 8101L);
        long createEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long messageExtId = 9101L;
        JdbiTestSupport.dao(jdbi, MessageEventDao.class).upsert(
                JdbiTestSupport.messageEvent(createEventId, messageExtId, "replay target", 0, false, 0));
        long ruleId = JdbiTestSupport.insertRule(jdbi, "message-replay", 3600);
        JdbiTestSupport.insertOutcome(jdbi, ruleId, 10, null);

        RuleEngine engine = ruleEngine(jdbi);

        engine.evaluate(RuleContext.forMessage(
                createEventId,
                memberId,
                channelId,
                JdbiTestSupport.messageEvent(createEventId, messageExtId, "replay target", 0, false, 0),
                7101L,
                false,
                null,
                0,
                0,
                8101L,
                "TEXT",
                null,
                null,
                null));

        MessageRewardReplayService replayService = replayService(jdbi, engine);

        assertTrue(replayService.replayMessage(messageExtId));
        assertTrue(replayService.replayMessage(messageExtId));

        int replayEvents = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM events WHERE event_type = 'MESSAGE_REPLAY'
                """);
        int replayEvaluations = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM rule_evaluations re
                JOIN events e ON e.id = re.event_id
                WHERE e.event_type = 'MESSAGE_REPLAY'
                """);
        int reversalRows = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger WHERE transaction_type = 'REVERSAL'
                """);
        int replayAwards = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger rl
                JOIN events e ON e.id = rl.event_id
                WHERE e.event_type = 'MESSAGE_REPLAY'
                  AND rl.transaction_type = 'AWARD'
                """);

        assertAll(
                () -> assertEquals(10, JdbiTestSupport.dao(jdbi, MemberDao.class).findPCurrency(memberId)),
                () -> assertEquals(2, replayEvents),
                () -> assertEquals(2, replayEvaluations),
                () -> assertEquals(0, reversalRows),
                () -> assertEquals(0, replayAwards));
    }

        @Test
        void replayAllActiveMessagesProcessesStoredMessagesInBatches() {
                Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
                long memberId = JdbiTestSupport.insertMember(jdbi, 7201L);
                long channelId = JdbiTestSupport.insertChannel(jdbi, 8201L);
                long firstEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
                long deletedEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
                long thirdEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
                MessageEventDao messageEventDao = JdbiTestSupport.dao(jdbi, MessageEventDao.class);

                long firstMessageId = messageEventDao.upsert(JdbiTestSupport.messageEvent(firstEventId, 9201L, "first replay", 0, false, 0));
                messageEventDao.upsert(JdbiTestSupport.messageEvent(deletedEventId, 9202L, "deleted replay", 0, false, 0));
                long thirdMessageId = messageEventDao.upsert(JdbiTestSupport.messageEvent(thirdEventId, 9203L, "third replay", 0, false, 0));
                messageEventDao.markDeleted(9202L);
                long ruleId = JdbiTestSupport.insertRule(jdbi, "message-replay-bulk", 3600);
                JdbiTestSupport.insertOutcome(jdbi, ruleId, 10, null);

                MessageRewardReplayService replayService = replayService(jdbi, ruleEngine(jdbi));
                MessageRewardReplayRunSummary summary = replayService.replayAllActiveMessages(1);

                int replayEvents = JdbiTestSupport.queryInt(jdbi, """
                                SELECT COUNT(*) FROM events WHERE event_type = 'MESSAGE_REPLAY'
                                """);
                int replayEvaluations = JdbiTestSupport.queryInt(jdbi, """
                                SELECT COUNT(*) FROM rule_evaluations re
                                JOIN events e ON e.id = re.event_id
                                WHERE e.event_type = 'MESSAGE_REPLAY'
                                """);

                assertAll(
                                () -> assertEquals(2, summary.batchesProcessed()),
                                () -> assertEquals(2, summary.scannedCount()),
                                () -> assertEquals(2, summary.replayedCount()),
                                () -> assertEquals(0, summary.failedCount()),
                                () -> assertEquals(thirdMessageId, summary.lastMessageId()),
                                () -> assertEquals(20, JdbiTestSupport.dao(jdbi, MemberDao.class).findPCurrency(memberId)),
                                () -> assertEquals(2, replayEvents),
                                () -> assertEquals(2, replayEvaluations),
                                () -> assertTrue(summary.lastMessageId() > firstMessageId));
        }

        private static RuleEngine ruleEngine(Jdbi jdbi) {
                RuleEngine engine = new RuleEngine();
                engine.ruleDao = JdbiTestSupport.dao(jdbi, RuleDao.class);
                engine.rulePredicateDao = JdbiTestSupport.dao(jdbi, RulePredicateDao.class);
                engine.ruleEvaluationDao = JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class);
                engine.jdbi = jdbi;
                @SuppressWarnings("unchecked")
                Instance<PredicateEvaluator> evaluatorInstance = mock(Instance.class);
                when(evaluatorInstance.iterator()).thenAnswer(invocation -> List.<PredicateEvaluator>of().iterator());
                engine.evaluatorBeans = evaluatorInstance;
                return engine;
        }

        private static MessageRewardReplayService replayService(Jdbi jdbi, RuleEngine engine) {
                MessageRewardReplayService replayService = new MessageRewardReplayService();
                replayService.messageEventDao = JdbiTestSupport.dao(jdbi, MessageEventDao.class);
                replayService.messageAttachmentDao = JdbiTestSupport.dao(jdbi, MessageAttachmentDao.class);
                replayService.eventDao = JdbiTestSupport.dao(jdbi, EventDao.class);
                replayService.memberDao = JdbiTestSupport.dao(jdbi, MemberDao.class);
                replayService.channelDao = JdbiTestSupport.dao(jdbi, ChannelDao.class);
                replayService.ruleEngine = engine;
                return replayService;
        }
}