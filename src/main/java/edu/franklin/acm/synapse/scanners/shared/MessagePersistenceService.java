package edu.franklin.acm.synapse.scanners.shared;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.jdbi.v3.core.Jdbi;

import edu.franklin.acm.synapse.activity.Event;
import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.message.MessageAttachment;
import edu.franklin.acm.synapse.activity.message.MessageAttachmentDao;
import edu.franklin.acm.synapse.activity.message.MessageEvent;
import edu.franklin.acm.synapse.activity.message.MessageEventDao;
import edu.franklin.acm.synapse.activity.message.MessageReaction;
import edu.franklin.acm.synapse.activity.message.MessageReactionDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Message;

/**
 * Transaction-wrapped persistence of a message and its metadata (attachments, reactions)
 * into the Event Lake. Used by both the live and historical scanners.
 *
 * <p>Does NOT fire rule evaluation — that is the caller's responsibility.
 */
@ApplicationScoped
public class MessagePersistenceService {

    @Inject Jdbi jdbi;

    /**
     * Persists an event, message row, attachments, and reactions atomically.
     *
     * @param memberInternalId  internal member ID (already upserted by caller)
     * @param channelInternalId internal channel ID (already upserted by caller)
     * @param threadInternalId  internal thread ID, or {@code null} for non-thread messages
     * @param m                 the JDA message
     * @return the generated event ID
     */
    @SuppressWarnings("null")
    public long persistMessage(long memberInternalId, long channelInternalId, Long threadInternalId, Message m) {
        return jdbi.inTransaction(handle -> {
            EventDao txEvent = handle.attach(EventDao.class);
            MessageEventDao txMsg = handle.attach(MessageEventDao.class);
            MessageAttachmentDao txAtt = handle.attach(MessageAttachmentDao.class);
            MessageReactionDao txRxn = handle.attach(MessageReactionDao.class);

            LocalDateTime discordCreatedAt = LocalDateTime.ofInstant(
                    m.getTimeCreated().toInstant(), ZoneOffset.UTC);
            long eventId = txEvent.insert(
                    new Event(0L, memberInternalId, channelInternalId, "MESSAGE_CREATE", discordCreatedAt.toString()));
            long messageId = txMsg.upsert(MessageEvent.fromDiscord(eventId, threadInternalId, m));

            if (!m.getAttachments().isEmpty()) {
                txAtt.deleteByMessageId(messageId);
                txAtt.insertBatch(m.getAttachments().stream()
                        .map(a -> MessageAttachment.fromDiscord(messageId, a))
                        .toList());
            }

            if (!m.getReactions().isEmpty()) {
                txRxn.deleteByMessageId(messageId);
                txRxn.insertBatch(m.getReactions().stream()
                        .map(r -> MessageReaction.fromDiscord(messageId, r))
                        .toList());
            }

            return eventId;
        });
    }
}
