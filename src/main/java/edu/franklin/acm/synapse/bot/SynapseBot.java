package edu.franklin.acm.synapse.bot;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SynapseBot implements EventListener {
    private static final Logger log = LoggerFactory.getLogger(SynapseBot.class);

    private final JDA jda;

    public SynapseBot(@ConfigProperty(name = "discord.token") String discordToken)
            throws InterruptedException {
        jda = JDABuilder.createDefault(discordToken)
                .addEventListeners(this)
                .build();

        jda.awaitReady();
    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        log.info("Received Discord Event: {}", genericEvent);
    }

    public long ping() {
        return jda.getGatewayPing();
    }
}
