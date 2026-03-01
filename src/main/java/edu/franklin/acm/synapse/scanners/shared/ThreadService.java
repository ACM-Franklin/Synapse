package edu.franklin.acm.synapse.scanners.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.thread.ForumTagDao;
import edu.franklin.acm.synapse.activity.thread.ThreadDao;
import edu.franklin.acm.synapse.activity.thread.ThreadTagDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;

/**
 * Service for upserting Discord threads and their forum tags into the database.
 *
 * <p>Provides two overloads of {@code upsertThread}: one that auto-resolves the
 * parent channel (for event handlers) and one that accepts a pre-resolved parent
 * channel internal ID (for batch scanning to avoid redundant lookups).
 */
@ApplicationScoped
public class ThreadService {

    private static final Logger log = LoggerFactory.getLogger(ThreadService.class);

    @Inject ThreadDao threadDao;
    @Inject ForumTagDao forumTagDao;
    @Inject ThreadTagDao threadTagDao;
    @Inject ChannelService channelService;

    /**
     * Upserts a thread with auto-resolution of the parent channel.
     *
     * @param thread the JDA ThreadChannel
     * @return the internal thread row ID
     */
    public long upsertThread(ThreadChannel thread) {
        long parentChannelInternalId = channelService.upsertChannel(thread.getParentChannel());
        return upsertThread(thread, parentChannelInternalId);
    }

    /**
     * Upserts a thread using a pre-resolved parent channel internal ID.
     * Avoids redundant channel lookups during batch scanning.
     *
     * @param thread                  the JDA ThreadChannel
     * @param parentChannelInternalId internal ID of the parent channel (already upserted)
     * @return the internal thread row ID
     */
    public long upsertThread(ThreadChannel thread, long parentChannelInternalId) {
        long threadInternalId = threadDao.upsert(
                thread.getIdLong(),
                parentChannelInternalId,
                thread.getOwnerIdLong() == 0L ? null : thread.getOwnerIdLong(),
                thread.getName(),
                thread.getType().name(),
                thread.isArchived(),
                thread.isLocked(),
                thread.isPinned(),
                thread.getMessageCount(),
                thread.getSlowmode(),
                thread.getAutoArchiveDuration().getMinutes(),
                thread.getTimeCreated().toInstant().toString());

        syncTags(threadInternalId, thread, parentChannelInternalId);
        return threadInternalId;
    }

    /**
     * Syncs applied forum tags for a thread. Only forum post threads carry tags;
     * for non-forum threads this is a no-op.
     */
    private void syncTags(long threadInternalId, ThreadChannel thread, long parentChannelInternalId) {
        var appliedTags = thread.getAppliedTags();
        if (appliedTags.isEmpty()) {
            return;
        }

        threadTagDao.deleteByThreadId(threadInternalId);
        for (ForumTag tag : appliedTags) {
            String emojiName = null;
            Long emojiExtId = null;
            var emoji = tag.getEmoji();
            if (emoji != null) {
                emojiName = emoji.getName();
                if (emoji instanceof CustomEmoji custom) {
                    emojiExtId = custom.getIdLong();
                }
            }

            long tagInternalId = forumTagDao.upsert(
                    tag.getIdLong(),
                    parentChannelInternalId,
                    tag.getName(),
                    emojiName,
                    emojiExtId,
                    tag.isModerated(),
                    tag.getTimeCreated().toInstant().toString());

            threadTagDao.insert(threadInternalId, tagInternalId);
        }

        log.debug("Synced {} tags for thread {} (ID: {})",
                appliedTags.size(), thread.getName(), thread.getIdLong());
    }
}
