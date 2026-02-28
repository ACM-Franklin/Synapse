package edu.franklin.acm.synapse.scanners.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.message.MessageEvent;
import edu.franklin.acm.synapse.rules.engine.RuleContext;
import edu.franklin.acm.synapse.rules.engine.RuleEvaluationRequest;
import edu.franklin.acm.synapse.scanners.shared.ChannelService;
import edu.franklin.acm.synapse.scanners.shared.MessagePersistenceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Message;

@ApplicationScoped
public class MessageIngestionHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageIngestionHandler.class);

    @Inject MemberDao memberDao;
    @Inject ChannelService channelService;
    @Inject MessagePersistenceService messagePersistenceService;

    @Inject
    jakarta.enterprise.event.Event<RuleEvaluationRequest> ruleEvents;

    public void handle(Message m) {
        long channelInternalId = channelService.upsertChannel(m.getChannel());
        long memberInternalId = memberDao.upsert(
                m.getAuthor().getIdLong(),
                m.getAuthor().getName(),
                m.getAuthor().isBot());

        long eventId = messagePersistenceService.persistMessage(
                memberInternalId, channelInternalId, m);

        log.debug("Ingested live message {} from {}", m.getId(), m.getAuthor().getName());

        String attFilename = m.getAttachments().isEmpty() ? null : m.getAttachments().get(0).getFileName();
        String attContentType = m.getAttachments().isEmpty() ? null : m.getAttachments().get(0).getContentType();
        RuleContext ctx = RuleContext.forMessage(
                eventId, memberInternalId, channelInternalId,
                MessageEvent.fromDiscord(eventId, m),
                m.getAuthor().getIdLong(),
                false, null,
                memberDao.findPCurrency(memberInternalId),
                memberDao.findSCurrency(memberInternalId),
                m.getChannel().getIdLong(),
                m.getChannel().getType().name(),
                null,
                attFilename, attContentType);
        ruleEvents.fireAsync(new RuleEvaluationRequest(ctx));
    }
}
