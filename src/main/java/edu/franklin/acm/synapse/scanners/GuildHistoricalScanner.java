package edu.franklin.acm.synapse.scanners;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.guild.GuildMetadataDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.scanners.shared.ChannelService;
import edu.franklin.acm.synapse.scanners.shared.MessagePersistenceService;
import edu.franklin.acm.synapse.scanners.shared.ThreadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * Backfills the Event Lake by scanning all message-bearing channels, threads,
 * and forum posts in a guild for historical messages.
 *
 * <p>Scanning proceeds in four phases:
 * <ol>
 *   <li><strong>Channels</strong> — text, news, voice, stage</li>
 *   <li><strong>Forums</strong> — forum container tags + active and archived posts</li>
 *   <li><strong>Active threads</strong> — text/news channel threads from JDA cache</li>
 *   <li><strong>Archived threads</strong> — text/news archived threads via REST</li>
 * </ol>
 *
 * <p>All data is normalized into real table columns — no JSON blobs.
 */
@ApplicationScoped
public class GuildHistoricalScanner {

    private static final int PAGE_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(GuildHistoricalScanner.class);

    @Inject MemberDao memberDao;
    @Inject GuildMetadataDao guildMetadataDao;
    @Inject ChannelService channelService;
    @Inject ThreadService threadService;
    @Inject MessagePersistenceService messagePersistenceService;

    /**
     * Scans all message-bearing channels and threads in a guild.
     *
     * @param guild             the guild to scan
     * @param lastSeenByChannel map of channel/thread ext_id to last-seen message ext_id
     * @return a future that completes when all scanning is done
     */
    public CompletableFuture<Void> scanGuild(Guild guild, Map<Long, Long> lastSeenByChannel) {
        return CompletableFuture.runAsync(() -> {
            guildMetadataDao.upsert(guild.getIdLong(), guild.getName(),
                    guild.getTimeCreated().toInstant().toString());
            log.info("Recorded guild metadata: {}", guild.getName());

            // Phase 1: Structural channels (text, news, voice, stage)
            List<MessageChannel> channels = new ArrayList<>();
            channels.addAll(guild.getTextChannels());
            channels.addAll(guild.getNewsChannels());
            channels.addAll(guild.getVoiceChannels());
            channels.addAll(guild.getStageChannels());

            for (MessageChannel channel : channels) {
                long startAfter = lastSeenByChannel.getOrDefault(channel.getIdLong(), 0L);
                log.info("Phase 1 — scanning channel: {} (ID: {})", channel.getName(), channel.getIdLong());
                scanChannelSync(channel, startAfter);
            }

            // Phase 2: Forum channels — sync tags, scan active + archived posts
            for (ForumChannel forum : guild.getForumChannels()) {
                log.info("Phase 2 — scanning forum: {} (ID: {})", forum.getName(), forum.getIdLong());
                scanForumSync(forum, lastSeenByChannel);
            }

            // Phase 3: Active threads in text/news channels (from JDA cache)
            for (ThreadChannel thread : guild.getThreadChannelCache()) {
                if (thread.getParentChannel() instanceof ForumChannel) {
                    continue; // already handled in Phase 2
                }
                long startAfter = lastSeenByChannel.getOrDefault(thread.getIdLong(), 0L);
                log.info("Phase 3 — scanning active thread: {} (ID: {}, parent: {})",
                        thread.getName(), thread.getIdLong(), thread.getParentChannel().getName());
                long parentChannelInternalId = channelService.upsertChannel(thread.getParentChannel());
                scanThreadSync(thread, parentChannelInternalId, startAfter);
            }

            // Phase 4: Archived threads in text/news channels (via REST)
            List<IThreadContainer> threadContainers = new ArrayList<>();
            threadContainers.addAll(guild.getTextChannels());
            threadContainers.addAll(guild.getNewsChannels());
            for (IThreadContainer container : threadContainers) {
                scanArchivedThreads(container, lastSeenByChannel);
            }

        }).exceptionally(ex -> {
            log.error("Fatal error during guild scan for {}", guild.getName(), ex);
            return null;
        });
    }

    /**
     * Scans a single structural channel for messages.
     */
    private void scanChannelSync(MessageChannel channel, long startAfterId) {
        long channelInternalId = channelService.upsertChannel(channel);
        long count = scanMessagesSync(channel, channelInternalId, null, startAfterId);
        log.info("Scanned {} messages from channel: {} (watermark: {})",
                count, channel.getName(), startAfterId);
    }

    /**
     * Upserts a thread and scans it for messages.
     */
    private void scanThreadSync(ThreadChannel thread, long parentChannelInternalId, long startAfterId) {
        long threadInternalId = threadService.upsertThread(thread, parentChannelInternalId);
        long count = scanMessagesSync(thread, parentChannelInternalId, threadInternalId, startAfterId);
        log.info("Scanned {} messages from thread: {} (watermark: {})",
                count, thread.getName(), startAfterId);
    }

    /**
     * Scans a ForumChannel: upserts the channel, syncs available tags, then scans
     * all active and archived forum posts.
     */
    private void scanForumSync(ForumChannel forum, Map<Long, Long> watermarks) {
        long forumInternalId = channelService.upsertChannel(forum);

        // Scan active forum posts (from JDA cache)
        for (ThreadChannel post : forum.getThreadChannels()) {
            long startAfter = watermarks.getOrDefault(post.getIdLong(), 0L);
            log.info("  Scanning active forum post: {} (ID: {})", post.getName(), post.getIdLong());
            scanThreadSync(post, forumInternalId, startAfter);
        }

        // Scan archived forum posts (via REST)
        try {
            List<ThreadChannel> archived = forum.retrieveArchivedPublicThreadChannels().stream().toList();
            for (ThreadChannel post : archived) {
                long startAfter = watermarks.getOrDefault(post.getIdLong(), 0L);
                log.info("  Scanning archived forum post: {} (ID: {})", post.getName(), post.getIdLong());
                scanThreadSync(post, forumInternalId, startAfter);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve archived forum posts for {}: {}", forum.getName(), e.getMessage());
        }
    }

    /**
     * Retrieves and scans archived threads from a text or news channel container.
     */
    private void scanArchivedThreads(IThreadContainer container, Map<Long, Long> watermarks) {
        MessageChannel parentChannel = (MessageChannel) container;
        long parentChannelInternalId = channelService.upsertChannel(parentChannel);

        try {
            List<ThreadChannel> archived = container.retrieveArchivedPublicThreadChannels().stream().toList();
            for (ThreadChannel thread : archived) {
                long startAfter = watermarks.getOrDefault(thread.getIdLong(), 0L);
                log.info("Phase 4 — scanning archived thread: {} (ID: {}, parent: {})",
                        thread.getName(), thread.getIdLong(), parentChannel.getName());
                scanThreadSync(thread, parentChannelInternalId, startAfter);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve archived threads for {}: {}", parentChannel.getName(), e.getMessage());
        }
    }

    /**
     * Shared pagination loop that scans messages from a MessageChannel and persists them.
     *
     * @param channel            the channel or thread to read messages from
     * @param channelInternalId  internal ID of the parent channel (for events table)
     * @param threadInternalId   internal ID of the thread, or null for non-thread channels
     * @param startAfterId       message ID to start after (0L scans from beginning)
     * @return number of messages processed
     */
    private long scanMessagesSync(MessageChannel channel, long channelInternalId,
                                  Long threadInternalId, long startAfterId) {
        long currentAfterId = startAfterId;
        long processedMessages = 0;

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
                persistMessage(m, channelInternalId, threadInternalId);
            }

            processedMessages += messages.size();
            currentAfterId = sorted.get(sorted.size() - 1).getIdLong();

            if (messages.size() < PAGE_SIZE) {
                break;
            }
        }

        return processedMessages;
    }

    private void persistMessage(Message m, long channelInternalId, Long threadInternalId) {
        long memberInternalId = memberDao.upsert(
                m.getAuthor().getIdLong(),
                m.getAuthor().getName(),
                m.getAuthor().isBot());

        messagePersistenceService.persistMessage(memberInternalId, channelInternalId, threadInternalId, m);
    }
}
