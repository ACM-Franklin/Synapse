package edu.franklin.acm.synapse.scanners;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.scanners.handlers.ChannelEventHandler;
import edu.franklin.acm.synapse.scanners.handlers.MemberEventHandler;
import edu.franklin.acm.synapse.scanners.handlers.MessageIngestionHandler;
import edu.franklin.acm.synapse.scanners.handlers.ReconciliationHandler;
import edu.franklin.acm.synapse.scanners.handlers.RoleEventHandler;
import edu.franklin.acm.synapse.scanners.handlers.VoiceEventHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateLockedEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateParentEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveAllEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEmojiEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Thin JDA gateway dispatcher. Receives Discord events and delegates to
 * domain-specific handlers for persistence and rule evaluation.
 *
 * <p>Registered as a JDA {@link ListenerAdapter} by {@code SynapseBot}.
 */
@ApplicationScoped
public class GuildLiveScanner extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GuildLiveScanner.class);

    @Inject MessageIngestionHandler messageHandler;
    @Inject MemberEventHandler memberHandler;
    @Inject VoiceEventHandler voiceHandler;
    @Inject ChannelEventHandler channelHandler;
    @Inject RoleEventHandler roleHandler;
    @Inject ReconciliationHandler reconciliationHandler;

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.isWebhookMessage()) return;
        if (!event.isFromGuild()) return;

        try {
            messageHandler.handle(event.getMessage());
        } catch (Exception e) {
            log.error("Failed to ingest live message {}", event.getMessage().getId(), e);
        }
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (!event.isFromGuild()) return;

        try {
            messageHandler.handleUpdate(event.getMessage());
        } catch (Exception e) {
            log.error("Failed to update live message snapshot {}", event.getMessageId(), e);
        }
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        if (!event.isFromGuild()) return;

        try {
            messageHandler.handleDelete(event.getMessageIdLong());
        } catch (Exception e) {
            log.error("Failed to process live message delete {}", event.getMessageId(), e);
        }
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (!event.isFromGuild()) return;

        try {
            messageHandler.handleReactionAdd(event.getReaction());
        } catch (Exception e) {
            log.error("Failed to process live reaction add on message {}", event.getMessageId(), e);
        }
    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        if (!event.isFromGuild()) return;

        try {
            messageHandler.handleReactionRemove(event.getReaction());
        } catch (Exception e) {
            log.error("Failed to process live reaction remove on message {}", event.getMessageId(), e);
        }
    }

    @Override
    public void onMessageReactionRemoveAll(@NotNull MessageReactionRemoveAllEvent event) {
        if (!event.isFromGuild()) return;

        try {
            messageHandler.handleReactionRemoveAll(event.getMessageIdLong());
        } catch (Exception e) {
            log.error("Failed to process live reaction remove-all on message {}", event.getMessageId(), e);
        }
    }

    @Override
    public void onMessageReactionRemoveEmoji(@NotNull MessageReactionRemoveEmojiEvent event) {
        if (!event.isFromGuild()) return;

        try {
            messageHandler.handleReactionRemoveEmoji(event.getReaction());
        } catch (Exception e) {
            log.error("Failed to process live reaction remove-emoji on message {}", event.getMessageId(), e);
        }
    }

    @Override
    public void onGuildMemberUpdate(@NotNull GuildMemberUpdateEvent event) {
        try {
            memberHandler.handleUpdate(event.getMember());
        } catch (Exception e) {
            log.error("Failed to process member update for {}", event.getMember().getUser().getName(), e);
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        try {
            memberHandler.handleJoin(event.getMember());
        } catch (Exception e) {
            log.error("Failed to process member join for {}", event.getMember().getUser().getName(), e);
        }
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        try {
            memberHandler.handleLeave(event.getUser());
        } catch (Exception e) {
            log.error("Failed to process member remove for {}", event.getUser().getName(), e);
        }
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        try {
            voiceHandler.handle(event);
        } catch (Exception e) {
            log.error("Failed to process voice update for {}", event.getMember().getUser().getName(), e);
        }
    }

    @Override
    public void onRoleCreate(@NotNull RoleCreateEvent event) {
        try {
            roleHandler.handleCreate(event.getRole());
        } catch (Exception e) {
            log.error("Failed to process role create for {}", event.getRole().getName(), e);
        }
    }

    @Override
    public void onRoleDelete(@NotNull RoleDeleteEvent event) {
        try {
            roleHandler.handleDelete(event.getRole());
        } catch (Exception e) {
            log.error("Failed to process role delete for {}", event.getRole().getName(), e);
        }
    }

    @Override
    public void onRoleUpdateName(@NotNull RoleUpdateNameEvent event) {
        try {
            roleHandler.handleNameUpdate(event.getRole());
        } catch (Exception e) {
            log.error("Failed to process role rename for {}", event.getRole().getName(), e);
        }
    }

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        try {
            channelHandler.handleCreate(event);
        } catch (Exception e) {
            log.error("Failed to process channel create for {}", event.getChannel().getName(), e);
        }
    }

    @Override
    public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        try {
            channelHandler.handleDelete(event);
        } catch (Exception e) {
            log.error("Failed to process channel delete for {}", event.getChannel().getName(), e);
        }
    }

    @Override
    public void onChannelUpdateName(@NotNull ChannelUpdateNameEvent event) {
        try {
            channelHandler.handleNameUpdate(event);
        } catch (Exception e) {
            log.error("Failed to process channel rename for {}", event.getChannel().getName(), e);
        }
    }

    @Override
    public void onChannelUpdateParent(@NotNull ChannelUpdateParentEvent event) {
        try {
            channelHandler.handleParentUpdate(event);
        } catch (Exception e) {
            log.error("Failed to process channel parent update for {}", event.getChannel().getName(), e);
        }
    }

    @Override
    public void onChannelUpdateArchived(@NotNull ChannelUpdateArchivedEvent event) {
        try {
            channelHandler.handleArchivedUpdate(event);
        } catch (Exception e) {
            log.error("Failed to process thread archive update for {}", event.getChannel().getName(), e);
        }
    }

    @Override
    public void onChannelUpdateLocked(@NotNull ChannelUpdateLockedEvent event) {
        try {
            channelHandler.handleLockedUpdate(event);
        } catch (Exception e) {
            log.error("Failed to process thread lock update for {}", event.getChannel().getName(), e);
        }
    }

    /**
     * Reconciles database state with the live guild after a restart.
     * Called once by {@code SynapseBot} after the JDA gateway is ready.
     */
    public void reconcile(Guild guild) throws Exception {
        reconciliationHandler.reconcile(guild);
    }
}
