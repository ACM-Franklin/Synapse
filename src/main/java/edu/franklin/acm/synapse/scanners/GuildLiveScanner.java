package edu.franklin.acm.synapse.scanners;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.ISnowflake;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.Event;
import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.member.MemberRoleChangeEvent;
import edu.franklin.acm.synapse.activity.member.MemberRoleDao;
import edu.franklin.acm.synapse.activity.message.MessageAttachment;
import edu.franklin.acm.synapse.activity.message.MessageAttachmentDao;
import edu.franklin.acm.synapse.activity.message.MessageEvent;
import edu.franklin.acm.synapse.activity.message.MessageEventDao;
import edu.franklin.acm.synapse.activity.message.MessageReaction;
import edu.franklin.acm.synapse.activity.message.MessageReactionDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionType;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Ingests real-time Discord events into the Event Lake and related normalized tables.
 * 
 * <p>Listens to the JDA gateway for {@code MESSAGE_CREATE} and {@code GUILD_MEMBER_UPDATE}
 * events, immediately persisting them to the database. This is the live counterpart to
 * {@link GuildHistoricalScanner}, which backfills historical data on demand.
 * 
 * <p><strong>Event Types:</strong>
 * <ul>
 *   <li>{@code MESSAGE_CREATE}: Persists message content, attachments, and reactions</li>
 *   <li>{@code GUILD_MEMBER_UPDATE}: Detects role changes and updates member profile data</li>
 * </ul>
 */
@ApplicationScoped
public class GuildLiveScanner extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GuildLiveScanner.class);

    // DAO dependencies for persisting ingested events
    @Inject EventDao eventDao;
    @Inject MemberDao memberDao;
    @Inject ChannelDao channelDao;
    @Inject MessageEventDao messageEventDao;
    @Inject MessageAttachmentDao messageAttachmentDao;
    @Inject MessageReactionDao messageReactionDao;
    @Inject MemberRoleDao memberRoleDao;

    // ========================================================================
    // JDA Event Handlers
    // ========================================================================

    /**
     * JDA event handler for MESSAGE_CREATE.
     * 
     * <p>Filters out webhook messages and non-guild messages, then persists the message
     * to the Event Lake. Exceptions are logged but do not halt ingestion.
     */
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.isWebhookMessage()) return;
        if (!event.isFromGuild()) return;

        try {
            processMessage(event.getMessage());
        } catch (Exception e) {
            log.error("Failed to ingest live message {}", event.getMessage().getId(), e);
        }
    }

    /**
     * JDA event handler for GUILD_MEMBER_UPDATE.
     * 
     * <p>Detects role changes and updates member profile metadata (nickname, avatar, boost status, etc.).
     * Exceptions are logged but do not halt event processing.
     */
    @Override
    public void onGuildMemberUpdate(@NotNull GuildMemberUpdateEvent event) {
        try {
            processMemberUpdate(event.getMember());
        } catch (Exception e) {
            log.error("Failed to process member update for {}", event.getMember().getUser().getName(), e);
        }
    }

    // ====================================================================
    // Message Processing
    // ====================================================================

    /**
     * Persists a live message and all its metadata to the Event Lake.
     * 
     * <p><strong>Workflow:</strong>
     * <ol>
     *   <li>Upsert the author (member) record</li>
     *   <li>Create a lean parent event record</li>
     *   <li>Upsert the normalized message_events row</li>
     *   <li>Persist attachments (delete-then-insert for idempotency)</li>
     *   <li>Persist a snapshot of reactions at ingestion time</li>
     * </ol>
     *
     * @param m the JDA message object to ingest
     */
    void processMessage(Message m) {
        long channelInternalId = channelDao.upsert(
                m.getChannel().getIdLong(),
                m.getChannel().getName(),
                null);
        long memberInternalId = memberDao.upsert(
                m.getAuthor().getIdLong(),
                m.getAuthor().getName(),
                m.getAuthor().isBot());

        // 1. Insert lean parent event row
        long eventId = eventDao.insert(
                new Event(0L, memberInternalId, channelInternalId, "MESSAGE_CREATE", null));

        // 2. Build and upsert the normalized message_events row
        long messageEventId = upsertMessageEvent(m, eventId);

        // 3. Attachments — delete-then-reinsert for idempotency on upsert
        persistAttachments(m, messageEventId);

        // 4. Reactions
        persistReactions(m, messageEventId);

        log.debug("Ingested live message {} from {}", m.getId(), m.getAuthor().getName());
    }

    @SuppressWarnings("null")
    private long upsertMessageEvent(Message m, long eventId) {
        final MessageEvent me = MessageEvent.fromDiscord(eventId, m);
        return messageEventDao.upsert(me);
    }

    /**
     * Persists message attachments with delete-then-insert semantics for idempotency on upsert.
     * 
     * <p>When a message is edited, attachments may change; this method ensures the
     * attachment list is always current by deleting all existing attachments for the
     * message and inserting the new ones in a single batch.
     *
     * @param m               the message to persist attachments from
     * @param messageEventId  the internal ID of the message_events row
     */
    @SuppressWarnings("null")
    private void persistAttachments(Message m, long messageEventId) {
        if (m.getAttachments().isEmpty()) return;

        messageAttachmentDao.deleteByMessageEventId(messageEventId);

        List<MessageAttachment> attachments = m
                .getAttachments()
                .stream()
                .map(a -> MessageAttachment.fromDiscord(messageEventId, a))
                .toList();

        messageAttachmentDao.insertBatch(attachments);
    }

    /**
     * Persists a snapshot of message reactions at ingestion time.
     * 
     * <p>Reactions are transient and may change frequently; this captures the current state
     * via delete-then-insert for consistency with the message's current state.
     *
     * @param m               the message to persist reactions from
     * @param messageEventId  the internal ID of the message_events row
     */
    @SuppressWarnings("null")
    private void persistReactions(Message m, long messageEventId) {
        if (m.getReactions().isEmpty()) return;

        messageReactionDao.deleteByMessageEventId(messageEventId);

        List<MessageReaction> reactions = m.getReactions().stream()
                .map(r -> MessageReaction.fromDiscord(messageEventId, r))
                .toList();

        messageReactionDao.insertBatch(reactions);
    }

    // ====================================================================
    // Member Update Processing
    // ====================================================================

    /**
     * Processes a member update event: detects role changes and persists profile changes.
     * 
     * <p><strong>Workflow:</strong>
     * <ol>
     *   <li>Upsert full member profile (nickname, avatar, boost status, etc.)</li>
     *   <li>Fetch the member's stored roles from the database</li>
     *   <li>Diff stored roles against current roles to detect additions and removals</li>
     *   <li>If roles changed: create parent event + role change detail event</li>
     *   <li>Always update the member_roles junction table to reflect current state</li>
     * </ol>
     *
     * @param member the updated member from the gateway event
     */
    private void processMemberUpdate(Member member) {
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

        // Detect role changes by diffing stored vs. current
        List<Long> storedRoles = memberRoleDao.findRolesByMemberId(memberId);
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

        if (!added.isEmpty() || !removed.isEmpty()) {
            // Write the parent event row
            long eventId = eventDao.insert(
                    new Event(0L, memberId, null, "MEMBER_ROLE_CHANGE", null));

            // Write the role change detail
            String addedStr = added.stream().map(String::valueOf).collect(Collectors.joining(","));
            String removedStr = removed.stream().map(String::valueOf).collect(Collectors.joining(","));
            memberRoleDao.insertRoleChangeEvent(new MemberRoleChangeEvent(0L, eventId, addedStr, removedStr));

            // Update the junction table to reflect current state
            memberRoleDao.deleteRoles(memberId);
            for (long roleExtId : currentRoleExtIds) {
                memberRoleDao.insertRole(memberId, roleExtId);
            }

            log.info("Recorded role change for {} — added: [{}], removed: [{}]",
                    member.getUser().getName(), addedStr, removedStr);
        } else {
            // No role change, but profile may have updated. Update junction table just in case.
            memberRoleDao.deleteRoles(memberId);
            for (long roleExtId : currentRoleExtIds) {
                memberRoleDao.insertRole(memberId, roleExtId);
            }
        }
    }
}
