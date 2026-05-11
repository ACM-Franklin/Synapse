package edu.franklin.acm.synapse.api.auth;

import java.time.Instant;
import java.util.Set;

/**
 * A logged-in user's session after Discord OAuth2 + guild-membership validation.
 *
 * <p>{@code roleExtIds} is captured at login time. It is not refreshed per-request;
 * if an admin role is removed, the session must expire or be invalidated before
 * the user loses admin access. Sessions are TTL-bounded so this gap is bounded.
 */
public record UserSession(
        String sessionId,
        long userExtId,
        String username,
        String globalName,
        String avatarHash,
        Set<Long> roleExtIds,
        boolean isAdmin,
        Instant createdAt,
        Instant expiresAt) {
}
