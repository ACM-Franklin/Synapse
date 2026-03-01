package edu.franklin.acm.synapse.scanners.handlers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.Event;
import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.channel.CategoryDao;
import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import edu.franklin.acm.synapse.activity.guild.GuildMetadataDao;
import edu.franklin.acm.synapse.activity.guild.SynapseStatisticsDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.member.RoleDao;
import edu.franklin.acm.synapse.activity.thread.ForumTagDao;
import edu.franklin.acm.synapse.activity.thread.ThreadDao;
import edu.franklin.acm.synapse.activity.voice.VoiceSessionDao;
import edu.franklin.acm.synapse.scanners.shared.ChannelService;
import edu.franklin.acm.synapse.scanners.shared.RoleSyncService;
import edu.franklin.acm.synapse.scanners.shared.ThreadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;

/**
 * Reconciles database state with the live guild after a restart.
 *
 * <ol>
 *   <li>Upsert guild metadata</li>
 *   <li>Fetch current members from Discord, then deactivate all and re-activate
 *       the fetched set.</li>
 *   <li>Close orphaned voice sessions, re-open for currently connected members</li>
 *   <li>Enumerate all guild channels and categories. Upsert all present; deactivate
 *       any in the DB that are no longer in Discord (set-difference).</li>
 *   <li>Sync forum tags, upsert active threads from JDA cache, deactivate threads
 *       no longer in the active set (set-difference).</li>
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
    @Inject ThreadService threadService;
    @Inject ChannelDao channelDao;
    @Inject CategoryDao categoryDao;
    @Inject ThreadDao threadDao;
    @Inject ForumTagDao forumTagDao;
    @Inject RoleSyncService roleSyncService;
    @Inject RoleDao roleDao;
    @Inject SynapseStatisticsDao statisticsDao;

    public void reconcile(Guild guild) throws Exception {
        log.info("Starting startup reconciliation for guild {}", guild.getName());

        reconcileGuildMetadata(guild);
        reconcileMembers(guild);
        reconcileRoles(guild);
        reconcileVoiceSessions(guild);
        reconcileChannelsAndCategories(guild);
        reconcileThreadsAndForumTags(guild);

        statisticsDao.recordReconciliation();
    }

    private void reconcileGuildMetadata(Guild guild) {
        guildMetadataDao.upsert(guild.getIdLong(), guild.getName(),
                guild.getTimeCreated().toInstant().toString());
    }

    /**
     * Fetches all members from Discord, then deactivates all DB members and
     * re-activates the fetched set. Fetching first keeps members active during
     * the Discord API round-trip.
     */
    private void reconcileMembers(Guild guild) throws Exception {
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
    }

    /**
     * Upserts all guild roles into the roles reference table and deactivates
     * any that no longer exist on Discord (set-difference).
     */
    private void reconcileRoles(Guild guild) {
        Set<Long> discordRoleIds = guild.getRoles().stream()
                .map(ISnowflake::getIdLong)
                .collect(Collectors.toSet());

        for (Role role : guild.getRoles()) {
            roleDao.upsert(role.getIdLong(), role.getName());
        }

        Set<Long> dbActiveRoleIds = new HashSet<>(roleDao.findAllActiveExtIds());
        Set<Long> deletedRoles = new HashSet<>(dbActiveRoleIds);
        deletedRoles.removeAll(discordRoleIds);
        if (!deletedRoles.isEmpty()) {
            roleDao.deactivateByExtIds(deletedRoles);
        }

        log.info("Reconciled {} roles ({} deactivated)",
                discordRoleIds.size(), deletedRoles.size());
    }

    /**
     * Closes orphaned voice sessions and re-opens for currently connected members.
     */
    private void reconcileVoiceSessions(Guild guild) {
        String now = utcNow();
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

    /**
     * Upserts all categories and non-thread channels from JDA's local cache,
     * then deactivates any that are no longer in Discord (set-difference).
     */
    private void reconcileChannelsAndCategories(Guild guild) {
        // Categories
        Set<Long> discordCategoryIds = guild.getCategories().stream()
                .map(ISnowflake::getIdLong)
                .collect(Collectors.toSet());
        Set<Long> deletedCategories = new HashSet<>(categoryDao.findAllActiveExtIds());
        deletedCategories.removeAll(discordCategoryIds);
        if (!deletedCategories.isEmpty()) {
            categoryDao.deactivateByExtIds(deletedCategories);
        }
        guild.getCategories().forEach(cat -> categoryDao.upsert(cat.getIdLong(), cat.getName(),
                cat.getTimeCreated().toInstant().toString()));

        // Channels (excluding categories and threads)
        Set<Long> discordChannelIds = guild.getChannels().stream()
                .filter(c -> c.getType() != ChannelType.CATEGORY && !c.getType().isThread())
                .map(ISnowflake::getIdLong)
                .collect(Collectors.toSet());
        Set<Long> deletedChannels = new HashSet<>(channelDao.findAllActiveExtIds());
        deletedChannels.removeAll(discordChannelIds);
        if (!deletedChannels.isEmpty()) {
            channelDao.deactivateByExtIds(deletedChannels);
        }
        guild.getChannels().stream()
                .filter(c -> c.getType() != ChannelType.CATEGORY && !c.getType().isThread())
                .forEach(channelService::upsertChannel);

        log.info("Reconciled {} channels across {} categories ({} channels deactivated, {} categories deactivated)",
                discordChannelIds.size(), discordCategoryIds.size(),
                deletedChannels.size(), deletedCategories.size());
    }

    /**
     * Syncs forum tag definitions, upserts active threads from JDA cache,
     * and deactivates threads no longer in the active set (set-difference).
     */
    private void reconcileThreadsAndForumTags(Guild guild) {
        int tagCount = 0;
        for (ForumChannel forum : guild.getForumChannels()) {
            long forumInternalId = channelService.upsertChannel(forum);
            for (ForumTag tag : forum.getAvailableTags()) {
                String emojiName = null;
                Long emojiExtId = null;
                var emoji = tag.getEmoji();
                if (emoji != null) {
                    emojiName = emoji.getName();
                    if (emoji instanceof CustomEmoji custom) {
                        emojiExtId = custom.getIdLong();
                    }
                }
                forumTagDao.upsert(tag.getIdLong(), forumInternalId, tag.getName(),
                        emojiName, emojiExtId, tag.isModerated(),
                        tag.getTimeCreated().toInstant().toString());
                tagCount++;
            }
        }

        Set<Long> discordThreadIds = guild.getThreadChannelCache().stream()
                .map(ISnowflake::getIdLong)
                .collect(Collectors.toSet());
        for (ThreadChannel thread : guild.getThreadChannelCache()) {
            threadService.upsertThread(thread);
        }

        Set<Long> deletedThreads = new HashSet<>(threadDao.findAllActiveExtIds());
        deletedThreads.removeAll(discordThreadIds);
        if (!deletedThreads.isEmpty()) {
            threadDao.deactivateByExtIds(deletedThreads);
        }

        log.info("Reconciled {} active threads, {} forum tags ({} threads deactivated)",
                discordThreadIds.size(), tagCount, deletedThreads.size());
    }

    private String utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).toString();
    }
}
