package edu.franklin.acm.synapse.api.auth;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Resolves Discord OAuth and session-related runtime configuration. Reading
 * config through a small bean keeps {@code @ConfigProperty} noise out of every
 * dependent class.
 */
@ApplicationScoped
public class AuthConfig {

    @ConfigProperty(name = "synapse.discord.oauth.client-id", defaultValue = "")
    String clientId;
    @ConfigProperty(name = "synapse.discord.oauth.client-secret", defaultValue = "")
    String clientSecret;
    @ConfigProperty(name = "synapse.discord.oauth.redirect-uri")
    String redirectUri;
    @ConfigProperty(name = "synapse.discord.oauth.frontend-redirect-uri")
    String frontendRedirectUri;
    @ConfigProperty(name = "synapse.discord.oauth.authorize-url")
    String authorizeUrl;
    @ConfigProperty(name = "synapse.discord.oauth.token-url")
    String tokenUrl;
    @ConfigProperty(name = "synapse.discord.oauth.api-base-url")
    String apiBaseUrl;

    @ConfigProperty(name = "synapse.discord.guild.id", defaultValue = "0")
    long guildId;

    @ConfigProperty(name = "synapse.auth.session.cookie-name", defaultValue = "synapse_session")
    String cookieName;
    @ConfigProperty(name = "synapse.auth.session.cookie-secure", defaultValue = "false")
    boolean cookieSecure;
    @ConfigProperty(name = "synapse.auth.session.ttl-seconds", defaultValue = "86400")
    long sessionTtlSeconds;
    @ConfigProperty(name = "synapse.auth.oauth.state-cookie-name", defaultValue = "synapse_oauth_state")
    String oauthStateCookieName;
    @ConfigProperty(name = "synapse.auth.oauth.state-ttl-seconds", defaultValue = "300")
    long oauthStateTtlSeconds;

    @ConfigProperty(name = "synapse.auth.admin-role-ids", defaultValue = "")
    Optional<String> adminRoleIdsRaw;

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public String redirectUri() {
        return redirectUri;
    }

    public String frontendRedirectUri() {
        return frontendRedirectUri;
    }

    public String authorizeUrl() {
        return authorizeUrl;
    }

    public String tokenUrl() {
        return tokenUrl;
    }

    public String apiBaseUrl() {
        return apiBaseUrl;
    }

    public long guildId() {
        return guildId;
    }

    public String cookieName() {
        return cookieName;
    }

    public boolean cookieSecure() {
        return cookieSecure;
    }

    public long sessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public String oauthStateCookieName() {
        return oauthStateCookieName;
    }

    public long oauthStateTtlSeconds() {
        return oauthStateTtlSeconds;
    }

    public Set<Long> adminRoleIds() {
        String raw = adminRoleIdsRaw.orElse("");
        if (raw.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String token : Arrays.asList(raw.split(","))) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            try {
                ids.add(Long.valueOf(trimmed));
            } catch (NumberFormatException ignored) {
                // Bad config entry; skip it rather than crash the bot.
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    public boolean isOauthConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
