package edu.franklin.acm.synapse.scanners.shared;

import edu.franklin.acm.synapse.activity.channel.CategoryDao;
import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;

@ApplicationScoped
public class ChannelService {

    @Inject ChannelDao channelDao;
    @Inject CategoryDao categoryDao;

    @SuppressWarnings("null")
    public long upsertChannel(Channel channel) {
        Long categoryId = null;
        if (channel instanceof ICategorizableChannel cat) {
            var category = cat.getParentCategory();
            if (category != null) {
                categoryId = categoryDao.upsert(category.getIdLong(), category.getName());
            }
        }
        return channelDao.upsert(
                channel.getIdLong(), channel.getName(), channel.getType().name(), categoryId);
    }
}
