package edu.franklin.acm.synapse.api.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Talks to Discord's OAuth2 and REST API on behalf of a user. Only the calls we
 * actually need are implemented — code-for-token exchange, identity lookup,
 * guild-member lookup. Everything is overridable for tests via the protected
 * {@code send} helper.
 */
@ApplicationScoped
public class DiscordOAuthClient {

    @Inject
    AuthConfig config;

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpClient http;

    @PostConstruct
    public void init() {
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String buildAuthorizeUrl(String state) {
        String scope = URLEncoder.encode("identify guilds guilds.members.read", StandardCharsets.UTF_8);
        return config.authorizeUrl()
                + "?response_type=code"
                + "&client_id=" + URLEncoder.encode(config.clientId(), StandardCharsets.UTF_8)
                + "&scope=" + scope
                + "&redirect_uri=" + URLEncoder.encode(config.redirectUri(), StandardCharsets.UTF_8)
                + "&prompt=none"
                + (state == null ? "" : "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8));
    }

    /**
     * Exchanges an OAuth2 authorization code for an access token.
     *
     * @throws DiscordAuthException if Discord rejects the code or returns a non-2xx response
     */
    public String exchangeCodeForAccessToken(String code) {
        String body = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(config.redirectUri(), StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(config.clientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(config.clientSecret(), StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(URI.create(config.tokenUrl()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = send(req);
        if (res.statusCode() / 100 != 2) {
            throw new DiscordAuthException("Token exchange failed: HTTP " + res.statusCode() + " " + res.body());
        }
        try {
            JsonNode json = mapper.readTree(res.body());
            JsonNode token = json.get("access_token");
            if (token == null || token.asText().isBlank()) {
                throw new DiscordAuthException("Token exchange response missing access_token");
            }
            return token.asText();
        } catch (DiscordAuthException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new DiscordAuthException("Failed to parse token response", e);
        }
    }

    /**
     * Returns the Discord user's identity for the given access token.
     */
    public DiscordUser fetchIdentity(String accessToken) {
        JsonNode json = getJson("/users/@me", accessToken);
        long id = parseSnowflake(json, "id");
        return new DiscordUser(
                id,
                textOrNull(json, "username"),
                textOrNull(json, "global_name"),
                textOrNull(json, "avatar"),
                json.path("bot").asBoolean(false));
    }

    /**
     * Returns the user's guild-member details (including role IDs) for the
     * configured guild, or {@code null} if the user is not a member.
     */
    public DiscordGuildMember fetchGuildMember(String accessToken, long guildId) {
        String path = "/users/@me/guilds/" + guildId + "/member";
        HttpRequest req = HttpRequest.newBuilder(URI.create(config.apiBaseUrl() + path))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> res = send(req);
        int status = res.statusCode();
        if (status == 404 || status == 403) {
            return null;
        }
        if (status / 100 != 2) {
            throw new DiscordAuthException("Guild member lookup failed: HTTP " + status + " " + res.body());
        }
        try {
            JsonNode json = mapper.readTree(res.body());
            Set<Long> roleIds = new HashSet<>();
            JsonNode roles = json.get("roles");
            if (roles != null && roles.isArray()) {
                for (JsonNode role : roles) {
                    try {
                        roleIds.add(Long.valueOf(role.asText()));
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed role IDs from Discord; they cannot match admin roles anyway.
                    }
                }
            }
            String nickname = textOrNull(json, "nick");
            return new DiscordGuildMember(nickname, roleIds);
        } catch (JsonProcessingException e) {
            throw new DiscordAuthException("Failed to parse guild member response", e);
        }
    }

    private JsonNode getJson(String path, String accessToken) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(config.apiBaseUrl() + path))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> res = send(req);
        if (res.statusCode() / 100 != 2) {
            throw new DiscordAuthException("Discord API call " + path + " failed: HTTP " + res.statusCode());
        }
        try {
            return mapper.readTree(res.body());
        } catch (JsonProcessingException e) {
            throw new DiscordAuthException("Failed to parse Discord response for " + path, e);
        }
    }

    /**
     * Test hook: subclass and override to mock HTTP without spinning up a fake server.
     */
    protected HttpResponse<String> send(HttpRequest req) {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DiscordAuthException("Discord HTTP call failed: " + req.method() + " " + req.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DiscordAuthException("Discord HTTP call interrupted: " + req.method() + " " + req.uri(), e);
        }
    }

    private static long parseSnowflake(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw new DiscordAuthException("Discord response missing field: " + field);
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            throw new DiscordAuthException("Discord response has unparseable " + field + ": " + value.asText(), e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    public record DiscordUser(long id, String username, String globalName, String avatarHash, boolean bot) {
    }

    public record DiscordGuildMember(String nickname, Set<Long> roleExtIds) {
    }

    public static class DiscordAuthException extends RuntimeException {
        public DiscordAuthException(String msg) {
            super(msg);
        }
        public DiscordAuthException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
