package edu.franklin.acm.synapse.scanners;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.Event;
import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import edu.franklin.acm.synapse.activity.guild.GuildMetadataDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.message.MessageAttachment;
import edu.franklin.acm.synapse.activity.message.MessageAttachmentDao;
import edu.franklin.acm.synapse.activity.message.MessageEvent;
import edu.franklin.acm.synapse.activity.message.MessageEventDao;
import edu.franklin.acm.synapse.activity.message.MessageReaction;
import edu.franklin.acm.synapse.activity.message.MessageReactionDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionType;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;

/**
 * Backfills the Event Lake by scanning a guild's text channels for historical messages
 * and persisting them to normalized SQL tables in batches.
 * 
 * <p><strong>Strategy:</strong> Scans forward chronologically from the oldest message
 * (or a checkpoint) to the present. Fetches messages in pages and persists each message
 * and its metadata (attachments, reactions) immediately for write consistency.
 * 
 * <p>All data is normalized into real table columns — no JSON blobs.
 */
@ApplicationScoped
public class GuildHistoricalScanner {

    private static final int PAGE_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(GuildHistoricalScanner.class);

    // DAO dependencies for persisting ingested data
    @Inject EventDao eventDao;
    @Inject MemberDao memberDao;
    @Inject GuildMetadataDao guildMetadataDao;
    @Inject ChannelDao channelDao;
    @Inject MessageEventDao messageEventDao;
    @Inject MessageAttachmentDao messageAttachmentDao;
    @Inject MessageReactionDao messageReactionDao;

    /**
     * Scans all text channels in a guild, recording guild metadata and backfilling historical messages.
     * 
     * <p>Resumes from a checkpoint in each channel (per-channel watermark), or from the
     * beginning if no checkpoint exists. Runs asynchronously and logs progress at each phase.
     *
     * @param guild                 the guild to scan
     * @param lastSeenByChannel     map of channel IDs to the last-seen message ext_id (0L if starting fresh)
     * @return                      a future that completes when all channels have been scanned
     */
    public CompletableFuture<Void> scanGuild(Guild guild, Map<Long, Long> lastSeenByChannel) {
        return CompletableFuture.runAsync(() -> {
            guildMetadataDao.upsert(guild.getIdLong(), guild.getName());
            log.info("Recorded guild metadata: {}", guild.getName());

            List<MessageChannel> channels = guild.getTextChannels().stream()
                    .map(c -> (MessageChannel) c)
                    .toList();

            for (MessageChannel channel : channels) {
                long startAfter = lastSeenByChannel.getOrDefault(channel.getIdLong(), 0L);
                log.info("Starting historical scan for channel: {} (ID: {})", channel.getName(), channel.getIdLong());

                scanChannelSync(channel, startAfter);

                log.info("Completed scanning channel: {}", channel.getName());
            }
        }).exceptionally(ex -> {
            log.error("Fatal error during guild scan for {}", guild.getName(), ex);
            return null;
        });
    }

    /**
     * Scans a single channel for all messages from a checkpoint forward, persisting each message.
     * 
     * <p>Blocks the calling thread; intended to be called from an async context.
     * Pagination happens in {@code PAGE_SIZE}-sized batches; loop continues until no more
     * messages are available or the page size is smaller than {@code PAGE_SIZE}.
     *
     * @param channel       the text channel to scan
     * @param startAfterId  the message ID to start after (0L scans from the beginning)
     */
    private void scanChannelSync(MessageChannel channel, long startAfterId) {
        long channelInternalId = channelDao.upsert(channel.getIdLong(), channel.getName(), null);
        long currentAfterId = startAfterId;

        while (true) {
            MessageHistory history = currentAfterId == 0L
                    ? channel.getHistoryFromBeginning(PAGE_SIZE).complete()
                    : channel.getHistoryAfter(currentAfterId, PAGE_SIZE).complete();

            List<Message> messages = history.getRetrievedHistory();
            if (messages.isEmpty()) {
                break;
            }

            List<Message> sorted = new ArrayList<>(messages);
            sorted.sort(Comparator.comparingLong(Message::getIdLong));

            for (Message m : sorted) {
                persistMessage(m, channelInternalId);
            }

            currentAfterId = sorted.get(sorted.size() - 1).getIdLong();
            log.info("Scanned batch of {} messages in channel: {} (watermark: {})",
                    messages.size(), channel.getName(), currentAfterId);

            if (messages.size() < PAGE_SIZE) {
                break;
            }
        }
    }

    /**
     * Persists a message and all its metadata (author, content, attachments, reactions) to the Event Lake.
     * 
     * <p><strong>Workflow:</strong>
     * <ol>
     *   <li>Upsert the member (author) record</li>
     *   <li>Create a lean parent event record</li>
     *   <li>Upsert the normalized message_events row</li>
     *   <li>Persist attachments (delete-then-insert for idempotency)</li>
     *   <li>Persist a snapshot of reactions at scan time</li>
     * </ol>
     *
     * @param m                 the JDA message object
     * @param channelInternalId the internal ID of the channel (already persisted)
     */
    @SuppressWarnings("null")
    private void persistMessage(Message m, long channelInternalId) {
        long memberInternalId = memberDao.upsert(m.getAuthor().getIdLong(), m.getAuthor().getName(), m.getAuthor().isBot());

        // 1. Lean parent event
        long eventId = eventDao.insert(new Event(0L, memberInternalId, channelInternalId, "MESSAGE_CREATE", null));

        // 2. Normalized message_events row
        var mentions = m.getMentions();
        var referencedMessage = m.getReferencedMessage();
        var timeEdited = m.getTimeEdited();
        var content = m.getContentRaw();

        MessageEvent me = new MessageEvent(
                0L,
                eventId,
                m.getIdLong(),
                m.getFlagsRaw(),
                content != null ? content.length() : 0,
                m.getType().getId(),
                m.getAttachments().size(),
                m.getReactions().stream().mapToInt(r -> r.getCount()).sum(),
                mentions.getUsers().size(),
                mentions.getRoles().size(),
                mentions.getChannels().size(),
                m.getEmbeds().size(),
                content,
                referencedMessage != null ? referencedMessage.getIdLong() : null,
                timeEdited != null ? LocalDateTime.ofInstant(timeEdited.toInstant(), ZoneOffset.UTC) : null,
                null,
                referencedMessage != null,
                m.getStartedThread() != null,
                !m.getAttachments().isEmpty(),
                mentions.mentionsEveryone(),
                m.isTTS(),
                m.isPinned(),
                !m.getStickers().isEmpty(),
                m.getPoll() != null,
                m.isVoiceMessage(),
                m.getAuthor().isBot()
        );

        long messageEventId = messageEventDao.upsert(me);

        // 3. Attachments
        if (!m.getAttachments().isEmpty()) {
            messageAttachmentDao.deleteByMessageEventId(messageEventId);

            List<MessageAttachment> attachments = m.getAttachments().stream()
                    .map(a -> new MessageAttachment(
                            0L,
                            messageEventId,
                            a.getIdLong(),
                            a.getFileName(),
                            a.getDescription(),
                            a.getContentType(),
                            a.getSize(),
                            a.getWidth(),
                            a.getHeight(),
                            a.getDuration() > 0 ? (double) a.getDuration() : null
                    ))
                    .toList();

            messageAttachmentDao.insertBatch(attachments);
        }

        // 4. Reactions
        if (!m.getReactions().isEmpty()) {
            messageReactionDao.deleteByMessageEventId(messageEventId);

            List<MessageReaction> reactions = m.getReactions().stream()
                    .map(r -> new MessageReaction(
                            0L,
                            messageEventId,
                            r.getEmoji().getName(),
                            r.getEmoji().getType() == Emoji.Type.CUSTOM
                                    ? r.getEmoji().asCustom().getIdLong() : null,
                            r.getCount(),
                            r.hasCount() ? r.getCount(ReactionType.SUPER) : 0
                    ))
                    .toList();

            messageReactionDao.insertBatch(reactions);
        }
    }
}
