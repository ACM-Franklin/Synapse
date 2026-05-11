package edu.franklin.acm.synapse.scanners.handlers;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.message.MessageEventDao;
import edu.franklin.acm.synapse.activity.message.MessageReactionDao;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerDao;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerEntry;
import edu.franklin.acm.synapse.activity.rules.RuleEvaluationDao;
import edu.franklin.acm.synapse.rules.engine.RuleEvaluationRequest;
import edu.franklin.acm.synapse.scanners.shared.ChannelService;
import edu.franklin.acm.synapse.scanners.shared.MessagePersistenceService;
import edu.franklin.acm.synapse.test.JdbiTestSupport;
import jakarta.enterprise.event.Event;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

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
                "MESSAGE",
                messageExtId,
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
    void deleteReversesUnreversedAwardsAcrossAllEventsForTheSameMessageSubject() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 9051L);
        long channelId = JdbiTestSupport.insertChannel(jdbi, 9052L);
        long createEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_CREATE");
        long updateEventId = JdbiTestSupport.insertEvent(jdbi, memberId, channelId, "MESSAGE_UPDATE");
        long messageExtId = 9053L;
        JdbiTestSupport.dao(jdbi, MessageEventDao.class).upsert(
                JdbiTestSupport.messageEvent(createEventId, messageExtId, "edited reward", 0, false, 0));

        long ruleId = JdbiTestSupport.insertRule(jdbi, "edit-reversal", 0);
        long outcomeId = JdbiTestSupport.insertOutcome(jdbi, ruleId, 10, null);
        long createEvaluationId = JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class).insert(ruleId, createEventId, memberId);
        long updateEvaluationId = JdbiTestSupport.dao(jdbi, RuleEvaluationDao.class).insert(ruleId, updateEventId, memberId);
        MemberDao memberDao = JdbiTestSupport.dao(jdbi, MemberDao.class);
        RewardLedgerDao rewardLedgerDao = JdbiTestSupport.dao(jdbi, RewardLedgerDao.class);

        memberDao.incrementPCurrency(memberId, 15);
        long createAwardId = rewardLedgerDao.insert(new RewardLedgerEntry(
                0L,
                createEvaluationId,
                outcomeId,
                ruleId,
                createEventId,
                memberId,
                "PRIMARY",
                5,
                "AWARD",
                null,
                "MESSAGE",
                messageExtId,
                null));
        long updateAwardId = rewardLedgerDao.insert(new RewardLedgerEntry(
                0L,
                updateEvaluationId,
                outcomeId,
                ruleId,
                updateEventId,
                memberId,
                "PRIMARY",
                10,
                "AWARD",
                null,
                "MESSAGE",
                messageExtId,
                null));

        MessageIngestionHandler handler = new MessageIngestionHandler();
        handler.jdbi = jdbi;

        handler.handleDelete(messageExtId);

        int createReversalRows = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger
                WHERE transaction_type = 'REVERSAL'
                  AND reverses_reward_ledger_id = :awardId
                """, "awardId", createAwardId);
        int updateReversalRows = JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM reward_ledger
                WHERE transaction_type = 'REVERSAL'
                  AND reverses_reward_ledger_id = :awardId
                """, "awardId", updateAwardId);

        assertAll(
                () -> assertEquals(1, createReversalRows),
                () -> assertEquals(1, updateReversalRows),
                () -> assertEquals(0, memberDao.findPCurrency(memberId)));
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

        @Test
        void updateFiresReplacementEvaluationRequestForTheStableMessageSubject() {
                MessageIngestionHandler handler = new MessageIngestionHandler();
                handler.memberDao = mock(MemberDao.class);
                handler.channelService = mock(ChannelService.class);
                handler.messagePersistenceService = mock(MessagePersistenceService.class);
                @SuppressWarnings("unchecked")
                Event<RuleEvaluationRequest> ruleEvents = mock(Event.class);
                handler.ruleEvents = ruleEvents;

                Message message = message(9301L, 9302L, 9303L, "edited body");

                when(handler.memberDao.upsert(9302L, "member-9302", false)).thenReturn(501L);
                when(handler.memberDao.findPCurrency(501L)).thenReturn(10);
                when(handler.memberDao.findSCurrency(501L)).thenReturn(4);
                when(handler.channelService.upsertChannel(any())).thenReturn(601L);
                when(handler.messagePersistenceService.updateMessageSnapshot(501L, 601L, null, message)).thenReturn(701L);
                when(ruleEvents.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

                handler.handleUpdate(message);

                ArgumentCaptor<RuleEvaluationRequest> requestCaptor = ArgumentCaptor.forClass(RuleEvaluationRequest.class);
                verify(ruleEvents).fireAsync(requestCaptor.capture());
                RuleEvaluationRequest request = requestCaptor.getValue();

                assertAll(
                                () -> assertEquals(true, request.replaceSubjectAwards()),
                                () -> assertEquals(701L, request.context().eventId()),
                                () -> assertEquals("MESSAGE", request.context().subjectType()),
                                () -> assertEquals(9301L, request.context().subjectExtId()),
                                () -> assertEquals(10, request.context().memberPCurrency()),
                                () -> assertEquals(4, request.context().memberSCurrency()));
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

        private static Message message(long messageExtId, long memberExtId, long channelExtId, String content) {
                Message message = mock(Message.class, RETURNS_DEEP_STUBS);
                User author = mock(User.class);
                MessageChannelUnion channel = mock(MessageChannelUnion.class);
                when(message.getIdLong()).thenReturn(messageExtId);
                when(message.getId()).thenReturn(Long.toString(messageExtId));
                when(message.getTimeCreated()).thenReturn(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
                when(message.getTimeEdited()).thenReturn(OffsetDateTime.parse("2026-01-01T00:05:00Z"));
                when(message.getAuthor()).thenReturn(author);
                when(author.getIdLong()).thenReturn(memberExtId);
                when(author.getName()).thenReturn("member-" + memberExtId);
                when(author.isBot()).thenReturn(false);
                when(message.getChannel()).thenReturn(channel);
                when(channel.getIdLong()).thenReturn(channelExtId);
                when(channel.getType()).thenReturn(ChannelType.TEXT);
                when(message.getFlagsRaw()).thenReturn(0L);
                when(message.getType()).thenReturn(MessageType.DEFAULT);
                when(message.getAttachments()).thenReturn(List.of());
                when(message.getReactions()).thenReturn(List.of());
                when(message.getEmbeds()).thenReturn(List.of());
                when(message.getContentRaw()).thenReturn(content);
                when(message.getReferencedMessage()).thenReturn(null);
                when(message.getStartedThread()).thenReturn(null);
                when(message.isTTS()).thenReturn(false);
                when(message.isPinned()).thenReturn(false);
                when(message.getStickers()).thenReturn(List.of());
                when(message.getPoll()).thenReturn(null);
                when(message.isVoiceMessage()).thenReturn(false);
                when(message.getMentions().getUsers()).thenReturn(List.of());
                when(message.getMentions().getRoles()).thenReturn(List.of());
                when(message.getMentions().getChannels()).thenReturn(List.of());
                when(message.getMentions().mentionsEveryone()).thenReturn(false);
                return message;
        }
}
