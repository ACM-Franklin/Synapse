package edu.franklin.acm.synapse.scanners.handlers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.Event;
import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.guild.GuildMetadataDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.voice.VoiceSessionDao;
import edu.franklin.acm.synapse.scanners.shared.ChannelService;
import edu.franklin.acm.synapse.scanners.shared.RoleSyncService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

/**
 * Reconciles database state with the live guild after a restart.
 *
 * <ol>
 *   <li>Upsert guild metadata</li>
 *   <li>Fetch current members from Discord, then deactivate all and re-activate
 *       the fetched set. Fetching first closes the race window where live events
 *       could arrive and see all members as inactive during the API round-trip.</li>
 *   <li>Close orphaned voice sessions, re-open for currently connected members</li>
 * </ol>
 */
@ApplicationScoped
public class ReconciliationHandler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationHandler.class);

    @Inject GuildMetadataDao guildMetadataDao;
    @Inject MemberDao memberDao;
    @Inject EventDao eventDao;
    @Inject VoiceSessionDao voiceSessionDao;
    @Inject ChannelService channelService;
    @Inject RoleSyncService roleSyncService;

    public void reconcile(Guild guild) throws Exception {
        String now = utcNow();
        log.info("Starting startup reconciliation for guild {}", guild.getName());

        // 1. Guild metadata
        guildMetadataDao.upsert(guild.getIdLong(), guild.getName());

        // 2. Member reconciliation — fetch first, then deactivate all and re-activate.
        // Fetching before deactivating keeps members active during the Discord API
        // round-trip; the deactivate→reactivate window is then only local DB operations.
        List<Member> members = guild.loadMembers().get();
        memberDao.deactivateAll();
        for (Member member : members) {
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
            roleSyncService.syncRoles(memberId, member);
        }
        log.info("Reconciled {} members", members.size());

        // 3. Voice reconciliation — close orphans, re-open for currently connected members
        voiceSessionDao.closeAllOrphaned(now);
        for (var voiceState : guild.getVoiceStates()) {
            var channel = voiceState.getChannel();
            if (channel == null) continue;
            Member member = voiceState.getMember();
            long memberId = memberDao.upsert(
                    member.getIdLong(),
                    member.getUser().getName(),
                    member.getUser().isBot());
            long channelId = channelService.upsertChannel(channel);

            long eventId = eventDao.insert(
                    new Event(0L, memberId, channelId, "VOICE_JOIN", null));
            voiceSessionDao.open(eventId, memberId, channelId, now);
        }
        log.info("Voice session reconciliation complete");
    }

    private String utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).toString();
    }
}
