package edu.franklin.acm.synapse.scanners.handlers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.Event;
import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.member.MemberRoleChangeEvent;
import edu.franklin.acm.synapse.activity.member.MemberRoleDao;
import edu.franklin.acm.synapse.activity.voice.VoiceSessionDao;
import edu.franklin.acm.synapse.rules.engine.RuleContext;
import edu.franklin.acm.synapse.rules.engine.RuleEvaluationRequest;
import edu.franklin.acm.synapse.scanners.shared.RoleSyncService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

@ApplicationScoped
public class MemberEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MemberEventHandler.class);

    @Inject
    MemberDao memberDao;
    @Inject
    MemberRoleDao memberRoleDao;
    @Inject
    EventDao eventDao;
    @Inject
    VoiceSessionDao voiceSessionDao;
    @Inject
    RoleSyncService roleSyncService;

    @Inject
    jakarta.enterprise.event.Event<RuleEvaluationRequest> ruleEvents;

    public void handleJoin(Member member) {
        var timeBoosted = member.getTimeBoosted();
        long memberId = memberDao.upsertFull(
                member.getIdLong(),
                member.getUser().getName(),
                member.getUser().getGlobalName(),
                member.getNickname(),
                member.getUser().getAvatarId(),
                member.getUser().isBot(),
                member.getTimeJoined().toString(),
                timeBoosted != null ? timeBoosted.toString() : null,
                member.isPending());

        LocalDateTime joinedAt = LocalDateTime.ofInstant(
                member.getTimeJoined().toInstant(), ZoneOffset.UTC);
        long eventId = eventDao.insert(new Event(0L, memberId, null, "MEMBER_JOIN", joinedAt.toString()));
        roleSyncService.syncRoles(memberId, member);

        log.info("Member joined: {}", member.getUser().getName());

        RuleContext ctx = RuleContext.forMemberEvent(
                "MEMBER_JOIN", eventId, memberId,
                member.getIdLong(),
                timeBoosted != null,
                member.getTimeJoined().toString(),
                memberDao.findPCurrency(memberId),
                memberDao.findSCurrency(memberId));
        ruleEvents.fireAsync(new RuleEvaluationRequest(ctx));
    }

    public void handleLeave(User user) {
        Long memberId = memberDao.findIdByExtId(user.getIdLong());
        if (memberId != null) {
            long eventId = eventDao.insert(new Event(0L, memberId, null, "MEMBER_LEAVE", null));
            voiceSessionDao.closeAllForMember(memberId, utcNow());

            RuleContext ctx = RuleContext.forMemberEvent(
                    "MEMBER_LEAVE", eventId, memberId,
                    user.getIdLong(), false, null,
                    memberDao.findPCurrency(memberId),
                    memberDao.findSCurrency(memberId));

            // Deactivate before firing async rules so the rule engine cannot
            // award currency to a member who has already left.
            memberDao.deactivate(user.getIdLong());
            ruleEvents.fireAsync(new RuleEvaluationRequest(ctx));
        } else {
            memberDao.deactivate(user.getIdLong());
        }

        log.info("Member left: {}", user.getName());
    }

    public void handleUpdate(Member member) {
        var timeBoosted = member.getTimeBoosted();
        long memberId = memberDao.upsertFull(
                member.getIdLong(),
                member.getUser().getName(),
                member.getUser().getGlobalName(),
                member.getNickname(),
                member.getUser().getAvatarId(),
                member.getUser().isBot(),
                member.getTimeJoined().toString(),
                timeBoosted != null ? timeBoosted.toString() : null,
                member.isPending()
        );

        detectAndRecordRoleChanges(member, memberId, timeBoosted != null);
        roleSyncService.syncRoles(memberId, member);
    }

    private void detectAndRecordRoleChanges(Member member, long memberId, boolean isBoosting) {
        List<Long> storedRoles = memberRoleDao.findRoleExtIdsByMemberId(memberId);
        Set<Long> currentRoleExtIds = member.getRoles().stream()
                .map(ISnowflake::getIdLong)
                .collect(Collectors.toSet());

        Set<Long> storedSet = Set.copyOf(storedRoles);
        List<Long> added = currentRoleExtIds.stream()
                .filter(r -> !storedSet.contains(r))
                .toList();
        List<Long> removed = storedRoles.stream()
                .filter(r -> !currentRoleExtIds.contains(r))
                .toList();

        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }

        long eventId = eventDao.insert(
                new Event(0L, memberId, null, "MEMBER_ROLE_CHANGE", null));

        String addedStr = added.stream().map(String::valueOf).collect(Collectors.joining(","));
        String removedStr = removed.stream().map(String::valueOf).collect(Collectors.joining(","));
        memberRoleDao.insertRoleChangeEvent(new MemberRoleChangeEvent(0L, eventId, addedStr, removedStr));

        log.info("Recorded role change for {} — added: [{}], removed: [{}]",
                member.getUser().getName(), addedStr, removedStr);

        RuleContext ctx = RuleContext.forRoleChange(
                eventId, memberId,
                member.getIdLong(),
                isBoosting,
                member.getTimeJoined().toString(),
                memberDao.findPCurrency(memberId),
                memberDao.findSCurrency(memberId),
                addedStr, removedStr);
        ruleEvents.fireAsync(new RuleEvaluationRequest(ctx));
    }

    private String utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).toString();
    }
}
