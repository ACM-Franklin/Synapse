package edu.franklin.acm.synapse.rules.engine;

import java.util.List;

import edu.franklin.acm.synapse.activity.Event;
import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import edu.franklin.acm.synapse.activity.channel.ChannelReplayTarget;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.message.MessageAttachment;
import edu.franklin.acm.synapse.activity.message.MessageAttachmentDao;
import edu.franklin.acm.synapse.activity.message.MessageEvent;
import edu.franklin.acm.synapse.activity.message.MessageEventDao;
import edu.franklin.acm.synapse.activity.message.MessageReplayCandidate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MessageRewardReplayService {

    @Inject MessageEventDao messageEventDao;
    @Inject MessageAttachmentDao messageAttachmentDao;
    @Inject EventDao eventDao;
    @Inject MemberDao memberDao;
    @Inject ChannelDao channelDao;
    @Inject RuleEngine ruleEngine;

    public MessageRewardReplayRunSummary replayAllActiveMessages(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }

        int batchesProcessed = 0;
        int scannedCount = 0;
        int replayedCount = 0;
        int failedCount = 0;
        long afterMessageId = 0L;

        while (true) {
            List<MessageReplayCandidate> candidates = messageEventDao.findReplayCandidatesAfterId(afterMessageId, batchSize + 1);
            boolean hasMore = candidates.size() > batchSize;
            int processCount = hasMore ? batchSize : candidates.size();
            if (processCount == 0) {
                return new MessageRewardReplayRunSummary(
                        batchesProcessed,
                        scannedCount,
                        replayedCount,
                        failedCount,
                        afterMessageId);
            }

            batchesProcessed++;
            scannedCount += processCount;
            for (int index = 0; index < processCount; index++) {
                MessageReplayCandidate candidate = candidates.get(index);
                afterMessageId = candidate.messageId();
                if (replayMessage(candidate.messageExtId())) {
                    replayedCount++;
                } else {
                    failedCount++;
                }
            }

            if (!hasMore) {
                return new MessageRewardReplayRunSummary(
                        batchesProcessed,
                        scannedCount,
                        replayedCount,
                        failedCount,
                        afterMessageId);
            }
        }
    }

    public boolean replayMessage(long messageExtId) {
        MessageEvent message = messageEventDao.findByExtId(messageExtId);
        if (message == null) {
            return false;
        }

        Event sourceEvent = eventDao.findById(message.eventId());
        if (sourceEvent == null || sourceEvent.channelId() == null) {
            return false;
        }

        ChannelReplayTarget channel = channelDao.findReplayTargetById(sourceEvent.channelId());
        if (channel == null) {
            return false;
        }

        MessageAttachment firstAttachment = messageAttachmentDao.findFirstByMessageId(message.id());
        long replayEventId = eventDao.insert(new Event(
                0L,
                sourceEvent.memberId(),
                sourceEvent.channelId(),
                "MESSAGE_REPLAY",
                null));

        RuleContext context = RuleContext.forMessage(
                replayEventId,
                sourceEvent.memberId(),
                sourceEvent.channelId(),
                withReplayEventId(message, replayEventId),
                memberDao.findExtIdById(sourceEvent.memberId()),
                memberDao.isBoosting(sourceEvent.memberId()),
                memberDao.findJoinedAt(sourceEvent.memberId()),
                memberDao.findPCurrency(sourceEvent.memberId()),
                memberDao.findSCurrency(sourceEvent.memberId()),
                channel.extId(),
                channel.type(),
                channel.categoryExtId(),
                firstAttachment != null ? firstAttachment.filename() : null,
                firstAttachment != null ? firstAttachment.contentType() : null);
        ruleEngine.evaluateReplacingSubject(context, true);
        return true;
    }

    private MessageEvent withReplayEventId(MessageEvent message, long replayEventId) {
        return new MessageEvent(
                message.id(),
                replayEventId,
                message.extId(),
                message.threadId(),
                message.flags(),
                message.contentLength(),
                message.type(),
                message.attachmentCount(),
                message.reactionCount(),
                message.mentionUserCount(),
                message.mentionRoleCount(),
                message.mentionChannelCount(),
                message.embedCount(),
                message.content(),
                message.referencedMessageExtId(),
                message.editedAt(),
                message.createdAt(),
                message.isReply(),
                message.spawnedThread(),
                message.hasAttachments(),
                message.mentionEveryone(),
                message.isTts(),
                message.isPinned(),
                message.hasStickers(),
                message.hasPoll(),
                message.isVoiceMessage(),
                message.authorIsBot());
    }
}