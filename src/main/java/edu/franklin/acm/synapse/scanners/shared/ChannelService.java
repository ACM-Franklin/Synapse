package edu.franklin.acm.synapse.scanners.shared;

import edu.franklin.acm.synapse.activity.channel.CategoryDao;
import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;

@ApplicationScoped
public class ChannelService {

    @Inject ChannelDao channelDao;
    @Inject CategoryDao categoryDao;

    @SuppressWarnings("null")
    public long upsertChannel(Channel channel) {
        if (channel instanceof ThreadChannel) {
            throw new IllegalArgumentException(
                    "ThreadChannel passed to upsertChannel — use ThreadService.upsertThread() instead. "
                    + "Thread: " + channel.getName() + " (ID: " + channel.getIdLong() + ")");
        }
        String createdAt = channel.getTimeCreated().toInstant().toString();
        Long categoryId = null;
        if (channel instanceof ICategorizableChannel cat) {
            var category = cat.getParentCategory();
            if (category != null) {
                categoryId = categoryDao.upsert(category.getIdLong(), category.getName(),
                        category.getTimeCreated().toInstant().toString());
            }
        }
        return channelDao.upsert(
                channel.getIdLong(), channel.getName(), channel.getType().name(),
                categoryId, createdAt);
    }
}
