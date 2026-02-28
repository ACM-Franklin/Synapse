package edu.franklin.acm.synapse.scanners.handlers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.Event;
import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.voice.VoiceSessionDao;
import edu.franklin.acm.synapse.rules.engine.RuleContext;
import edu.franklin.acm.synapse.rules.engine.RuleEvaluationRequest;
import edu.franklin.acm.synapse.scanners.shared.ChannelService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;

@ApplicationScoped
public class VoiceEventHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceEventHandler.class);

    @Inject MemberDao memberDao;
    @Inject EventDao eventDao;
    @Inject VoiceSessionDao voiceSessionDao;
    @Inject ChannelService channelService;

    @Inject
    jakarta.enterprise.event.Event<RuleEvaluationRequest> ruleEvents;

    /**
     * Routes a voice state update to the appropriate handler based on channel nullity.
     */
    public void handle(GuildVoiceUpdateEvent event) {
        AudioChannelUnion joined = event.getChannelJoined();
        AudioChannelUnion left = event.getChannelLeft();
        Member member = event.getMember();

        if (left == null && joined != null) {
            handleJoin(member, joined);
        } else if (left != null && joined == null) {
            handleLeave(member, left);
        } else if (left != null && joined != null) {
            handleMove(member, left, joined);
        }
    }

    private void handleJoin(Member member, AudioChannelUnion channel) {
        String now = utcNow();
        long memberId = memberDao.upsert(
                member.getIdLong(),
                member.getUser().getName(),
                member.getUser().isBot());
        long channelId = channelService.upsertChannel(channel);

        long eventId = eventDao.insert(
                new Event(0L, memberId, channelId, "VOICE_JOIN", null));
        voiceSessionDao.open(eventId, memberId, channelId, now);

        log.debug("Voice join: {} -> {}", member.getUser().getName(), channel.getName());

        RuleContext ctx = RuleContext.forVoiceEvent(
                "VOICE_JOIN", eventId, memberId, channelId,
                member.getIdLong(), false, null,
                memberDao.findPCurrency(memberId),
                memberDao.findSCurrency(memberId),
                channel.getIdLong(), channel.getType().name(), null);
        ruleEvents.fireAsync(new RuleEvaluationRequest(ctx));
    }

    private void handleLeave(Member member, AudioChannelUnion channel) {
        String now = utcNow();
        long memberId = memberDao.upsert(
                member.getIdLong(),
                member.getUser().getName(),
                member.getUser().isBot());
        long channelId = channelService.upsertChannel(channel);

        voiceSessionDao.close(memberId, channelId, now);
        long eventId = eventDao.insert(new Event(0L, memberId, channelId, "VOICE_LEAVE", null));

        log.debug("Voice leave: {} <- {}", member.getUser().getName(), channel.getName());

        RuleContext ctx = RuleContext.forVoiceEvent(
                "VOICE_LEAVE", eventId, memberId, channelId,
                member.getIdLong(), false, null,
                memberDao.findPCurrency(memberId),
                memberDao.findSCurrency(memberId),
                channel.getIdLong(), channel.getType().name(), null);
        ruleEvents.fireAsync(new RuleEvaluationRequest(ctx));
    }

    private void handleMove(Member member, AudioChannelUnion oldChannel, AudioChannelUnion newChannel) {
        String now = utcNow();
        long memberId = memberDao.upsert(
                member.getIdLong(),
                member.getUser().getName(),
                member.getUser().isBot());
        long oldChannelId = channelService.upsertChannel(oldChannel);
        long newChannelId = channelService.upsertChannel(newChannel);

        voiceSessionDao.close(memberId, oldChannelId, now);

        long eventId = eventDao.insert(
                new Event(0L, memberId, newChannelId, "VOICE_MOVE", null));
        voiceSessionDao.open(eventId, memberId, newChannelId, now);

        log.debug("Voice move: {} from {} to {}",
                member.getUser().getName(), oldChannel.getName(), newChannel.getName());

        RuleContext ctx = RuleContext.forVoiceEvent(
                "VOICE_MOVE", eventId, memberId, newChannelId,
                member.getIdLong(), false, null,
                memberDao.findPCurrency(memberId),
                memberDao.findSCurrency(memberId),
                newChannel.getIdLong(), newChannel.getType().name(), null);
        ruleEvents.fireAsync(new RuleEvaluationRequest(ctx));
    }

    private String utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).toString();
    }
}
