package edu.franklin.acm.synapse.api.auth;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Discord OAuth2 login orchestration.
 *
 * <p>Owns the policy decisions: bots are rejected, non-members are rejected,
 * members get a session, and admin status is derived from configured role IDs.
 */
@ApplicationScoped
public class AuthService {

    @Inject AuthConfig config;
    @Inject DiscordOAuthClient discord;
    @Inject SessionStore sessions;

    public String buildAuthorizeUrl(String state, String codeChallenge) {
        if (!config.isOauthConfigured()) {
            throw new WebApplicationException(
                    "Discord OAuth is not configured on this server.",
                    Response.Status.SERVICE_UNAVAILABLE);
        }
        return discord.buildAuthorizeUrl(state, codeChallenge);
    }

    /**
     * Completes the OAuth callback by exchanging the code, validating guild
     * membership, and minting a session.
     *
     * @throws WebApplicationException 401 if the user is not a guild member,
     *         403 if the account is a bot, 503 if OAuth is not configured.
     */
    public UserSession completeLogin(String code, String codeVerifier) {
        if (!config.isOauthConfigured()) {
            throw new WebApplicationException(
                    "Discord OAuth is not configured on this server.",
                    Response.Status.SERVICE_UNAVAILABLE);
        }

        String accessToken = discord.exchangeCodeForAccessToken(code, codeVerifier);
        DiscordOAuthClient.DiscordUser user = discord.fetchIdentity(accessToken);

        if (user.bot()) {
            throw new WebApplicationException("Bot accounts cannot sign in.", Response.Status.FORBIDDEN);
        }
        if (config.guildId() <= 0) {
            throw new WebApplicationException(
                    "This Synapse instance is not bound to a guild.",
                    Response.Status.SERVICE_UNAVAILABLE);
        }

        DiscordOAuthClient.DiscordGuildMember member = discord.fetchGuildMember(accessToken, config.guildId());
        if (member == null) {
            throw new WebApplicationException(
                    "You are not a member of this guild.", Response.Status.FORBIDDEN);
        }

        boolean isAdmin = matchesAdminRole(member.roleExtIds(), config.adminRoleIds());
        return sessions.create(
                user.id(),
                user.username(),
                user.globalName(),
                user.avatarHash(),
                member.roleExtIds(),
                isAdmin);
    }

    private static boolean matchesAdminRole(Set<Long> userRoles, Set<Long> adminRoles) {
        if (adminRoles.isEmpty()) {
            return false;
        }
        for (Long roleId : adminRoles) {
            if (userRoles.contains(roleId)) {
                return true;
            }
        }
        return false;
    }
}
