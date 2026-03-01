package edu.franklin.acm.synapse.scanners.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.channel.CategoryDao;
import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import edu.franklin.acm.synapse.activity.thread.ThreadDao;
import edu.franklin.acm.synapse.scanners.shared.ChannelService;
import edu.franklin.acm.synapse.scanners.shared.ThreadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateLockedEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateParentEvent;

/**
 * Handles live Discord channel, category, and thread structural events to keep
 * the database in parity with the guild. Covers creation, deletion, rename,
 * re-categorization, and thread archive/lock state changes.
 *
 * <p>Startup parity is handled separately by {@link ReconciliationHandler}.
 */
@ApplicationScoped
public class ChannelEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ChannelEventHandler.class);

    @Inject ChannelService channelService;
    @Inject ThreadService threadService;
    @Inject ChannelDao channelDao;
    @Inject CategoryDao categoryDao;
    @Inject ThreadDao threadDao;

    public void handleCreate(ChannelCreateEvent event) {
        Channel channel = event.getChannel();
        switch (channel) {
            case Category cat -> {
                categoryDao.upsert(cat.getIdLong(), cat.getName(),
                        cat.getTimeCreated().toInstant().toString());
                log.info("Recorded new category: {} ({})", cat.getName(), cat.getIdLong());
            }
            case ThreadChannel thread -> {
                threadService.upsertThread(thread);
                log.info("Recorded new thread: {} ({}, type: {})",
                        thread.getName(), thread.getIdLong(), thread.getType().name());
            }
            default -> {
                channelService.upsertChannel(channel);
                log.info("Recorded new channel: {} ({})", channel.getName(), channel.getIdLong());
            }
        }
    }

    public void handleDelete(ChannelDeleteEvent event) {
        Channel channel = event.getChannel();
        switch (channel) {
            case Category _ -> {
                categoryDao.markInactive(channel.getIdLong());
                log.info("Deactivated deleted category: {} ({})", channel.getName(), channel.getIdLong());
            }
            case ThreadChannel _ -> {
                threadDao.markInactive(channel.getIdLong());
                log.info("Deactivated deleted thread: {} ({})", channel.getName(), channel.getIdLong());
            }
            default -> {
                channelDao.markInactive(channel.getIdLong());
                log.info("Deactivated deleted channel: {} ({})", channel.getName(), channel.getIdLong());
            }
        }
    }

    public void handleNameUpdate(ChannelUpdateNameEvent event) {
        Channel channel = event.getChannel();
        switch (channel) {
            case Category cat -> {
                categoryDao.upsert(cat.getIdLong(), cat.getName(),
                        cat.getTimeCreated().toInstant().toString());
                log.info("Updated category name: {} -> {} ({})",
                        event.getOldValue(), event.getNewValue(), cat.getIdLong());
            }
            case ThreadChannel thread -> {
                threadService.upsertThread(thread);
                log.info("Updated thread name: {} -> {} ({})",
                        event.getOldValue(), event.getNewValue(), thread.getIdLong());
            }
            default -> {
                channelService.upsertChannel(channel);
                log.info("Updated channel name: {} -> {} ({})",
                        event.getOldValue(), event.getNewValue(), channel.getIdLong());
            }
        }
    }

    @SuppressWarnings("null")
    public void handleParentUpdate(ChannelUpdateParentEvent event) {
        channelService.upsertChannel(event.getChannel());
        log.info("Updated channel parent: {} moved to category {} ({})",
                event.getChannel().getName(),
                event.getNewValue() != null ? event.getNewValue().getName() : "none",
                event.getChannel().getIdLong());
    }

    public void handleArchivedUpdate(ChannelUpdateArchivedEvent event) {
        if (event.getChannel() instanceof ThreadChannel thread) {
            threadService.upsertThread(thread);
            log.info("Thread {} archived state changed to {} ({})",
                    thread.getName(), thread.isArchived(), thread.getIdLong());
        }
    }

    public void handleLockedUpdate(ChannelUpdateLockedEvent event) {
        if (event.getChannel() instanceof ThreadChannel thread) {
            threadService.upsertThread(thread);
            log.info("Thread {} locked state changed to {} ({})",
                    thread.getName(), thread.isLocked(), thread.getIdLong());
        }
    }
}
