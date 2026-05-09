package edu.franklin.acm.synapse.scanners.handlers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.Event;
import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.message.MessageDeletionTarget;
import edu.franklin.acm.synapse.activity.message.MessageEvent;
import edu.franklin.acm.synapse.activity.message.MessageEventDao;
import edu.franklin.acm.synapse.activity.message.MessageReactionDao;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerDao;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerEntry;
import edu.franklin.acm.synapse.rules.engine.RuleContext;
import edu.franklin.acm.synapse.rules.engine.RuleEvaluationRequest;
import edu.franklin.acm.synapse.scanners.shared.ChannelService;
import edu.franklin.acm.synapse.scanners.shared.MessagePersistenceService;
import edu.franklin.acm.synapse.scanners.shared.ThreadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;

@ApplicationScoped
public class MessageIngestionHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageIngestionHandler.class);

    @Inject MemberDao memberDao;
    @Inject MessageEventDao messageEventDao;
    @Inject MessageReactionDao messageReactionDao;
    @Inject Jdbi jdbi;
    @Inject ChannelService channelService;
    @Inject ThreadService threadService;
    @Inject MessagePersistenceService messagePersistenceService;

    @Inject
    jakarta.enterprise.event.Event<RuleEvaluationRequest> ruleEvents;

    public void handle(Message m) {
        long channelInternalId;
        Long threadInternalId = null;

        if (m.getChannel() instanceof ThreadChannel thread) {
            channelInternalId = channelService.upsertChannel(thread.getParentChannel());
            threadInternalId = threadService.upsertThread(thread, channelInternalId);
        } else {
            channelInternalId = channelService.upsertChannel(m.getChannel());
        }

        long memberInternalId = memberDao.upsert(
                m.getAuthor().getIdLong(),
                m.getAuthor().getName(),
                m.getAuthor().isBot());

        long eventId = messagePersistenceService.persistMessage(
                memberInternalId, channelInternalId, threadInternalId, m);

        log.debug("Ingested live message {} from {}", m.getId(), m.getAuthor().getName());

        String attFilename = m.getAttachments().isEmpty() ? null : m.getAttachments().get(0).getFileName();
        String attContentType = m.getAttachments().isEmpty() ? null : m.getAttachments().get(0).getContentType();
        RuleContext ctx = RuleContext.forMessage(
                eventId, memberInternalId, channelInternalId,
                MessageEvent.fromDiscord(eventId, threadInternalId, m),
                m.getAuthor().getIdLong(),
                false, null,
                memberDao.findPCurrency(memberInternalId),
                memberDao.findSCurrency(memberInternalId),
                m.getChannel() instanceof ThreadChannel tc
                        ? tc.getParentChannel().getIdLong()
                        : m.getChannel().getIdLong(),
                m.getChannel() instanceof ThreadChannel tc2
                        ? tc2.getParentChannel().getType().name()
                        : m.getChannel().getType().name(),
                null,
                attFilename, attContentType);
        ruleEvents.fireAsync(new RuleEvaluationRequest(ctx));
    }

    public void handleUpdate(Message m) {
        long channelInternalId;
        Long threadInternalId = null;

        if (m.getChannel() instanceof ThreadChannel thread) {
            channelInternalId = channelService.upsertChannel(thread.getParentChannel());
            threadInternalId = threadService.upsertThread(thread, channelInternalId);
        } else {
            channelInternalId = channelService.upsertChannel(m.getChannel());
        }

        long memberInternalId = memberDao.upsert(
                m.getAuthor().getIdLong(),
                m.getAuthor().getName(),
                m.getAuthor().isBot());

        messagePersistenceService.updateMessageSnapshot(memberInternalId, channelInternalId, threadInternalId, m);
        log.debug("Updated live message snapshot {} from {}", m.getId(), m.getAuthor().getName());
    }

    public void handleDelete(long messageExtId) {
        jdbi.useTransaction(handle -> {
            MessageEventDao txMessageDao = attachDao(handle, MessageEventDao.class);
            EventDao txEventDao = attachDao(handle, EventDao.class);
            RewardLedgerDao txRewardLedgerDao = attachDao(handle, RewardLedgerDao.class);
            MemberDao txMemberDao = attachDao(handle, MemberDao.class);

            MessageDeletionTarget target = txMessageDao.findDeletionTargetByExtId(messageExtId);
            if (target == null) {
                log.debug("Ignored delete for unknown message {}", messageExtId);
                return;
            }

            long deleteEventId = txEventDao.insert(new Event(
                    0L,
                    target.memberId(),
                    target.channelId(),
                    "MESSAGE_DELETE",
                    LocalDateTime.now(ZoneOffset.UTC).toString()));
            txMessageDao.markDeleted(messageExtId);

            for (RewardLedgerEntry award : txRewardLedgerDao.findUnreversedAwardsByEventId(target.eventId())) {
                txRewardLedgerDao.insert(new RewardLedgerEntry(
                        0L,
                        award.ruleEvaluationId(),
                        award.ruleOutcomeId(),
                        award.ruleId(),
                        deleteEventId,
                        award.memberId(),
                        award.currencyType(),
                        -award.amount(),
                        "REVERSAL",
                        award.id(),
                        null));
                reverseMemberBalance(txMemberDao, award);
            }
        });
    }

    public void handleReactionAdd(net.dv8tion.jda.api.entities.MessageReaction reaction) {
        Long messageId = messageEventDao.findIdByExtId(reaction.getMessageIdLong());
        if (messageId == null) {
            log.debug("Ignored reaction add for unknown message {}", reaction.getMessageId());
            return;
        }
        messageReactionDao.incrementCount(messageId, reaction.getEmoji().getName(), customEmojiExtId(reaction));
        messageEventDao.incrementReactionCount(messageId);
    }

    public void handleReactionRemove(net.dv8tion.jda.api.entities.MessageReaction reaction) {
        Long messageId = messageEventDao.findIdByExtId(reaction.getMessageIdLong());
        if (messageId == null) {
            log.debug("Ignored reaction remove for unknown message {}", reaction.getMessageId());
            return;
        }
        messageReactionDao.decrementCount(messageId, reaction.getEmoji().getName(), customEmojiExtId(reaction));
        messageEventDao.decrementReactionCount(messageId);
    }

    public void handleReactionRemoveAll(long messageExtId) {
        Long messageId = messageEventDao.findIdByExtId(messageExtId);
        if (messageId == null) {
            log.debug("Ignored reaction remove-all for unknown message {}", messageExtId);
            return;
        }
        messageReactionDao.deleteByMessageId(messageId);
        messageEventDao.clearReactionCount(messageId);
    }

    public void handleReactionRemoveEmoji(net.dv8tion.jda.api.entities.MessageReaction reaction) {
        handleReactionRemoveEmoji(
                reaction.getMessageIdLong(),
                reaction.getEmoji().getName(),
                customEmojiExtId(reaction));
    }

    void handleReactionRemoveEmoji(long messageExtId, String emojiName, Long emojiExtId) {
        Long messageId = messageEventDao.findIdByExtId(messageExtId);
        if (messageId == null) {
            log.debug("Ignored reaction remove-emoji for unknown message {}", messageExtId);
            return;
        }

        int removedCount = messageReactionDao.findCount(messageId, emojiName, emojiExtId);
        messageReactionDao.deleteEmoji(messageId, emojiName, emojiExtId);
        messageEventDao.decrementReactionCountBy(messageId, removedCount);
    }

    private void reverseMemberBalance(MemberDao txMemberDao, RewardLedgerEntry award) {
        switch (award.currencyType()) {
            case "PRIMARY" -> txMemberDao.incrementPCurrency(award.memberId(), -award.amount());
            case "SECONDARY" -> txMemberDao.incrementSCurrency(award.memberId(), -award.amount());
            default -> log.warn("Cannot reverse unknown currency type {} on reward ledger {}",
                    award.currencyType(), award.id());
        }
    }

    private Long customEmojiExtId(net.dv8tion.jda.api.entities.MessageReaction reaction) {
        return reaction.getEmoji().getType() == Emoji.Type.CUSTOM
                ? reaction.getEmoji().asCustom().getIdLong()
                : null;
    }

    @SuppressWarnings("null")
    private static <T> T attachDao(Handle handle, Class<T> daoType) {
        return handle.attach(daoType);
    }
}
