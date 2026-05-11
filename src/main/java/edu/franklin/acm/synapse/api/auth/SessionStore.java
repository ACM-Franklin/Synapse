package edu.franklin.acm.synapse.api.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * In-memory session store. Single-instance bot, single guild — a process-local
 * map is enough. Sessions expire on TTL or explicit logout. Restarting the bot
 * invalidates every session, which is desirable here because the OAuth contract
 * with Discord is not durable across restarts anyway.
 */
@ApplicationScoped
public class SessionStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public SessionStore(@ConfigProperty(name = "synapse.auth.session.ttl-seconds", defaultValue = "86400") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public UserSession create(long userExtId, String username, String globalName, String avatarHash,
                              Set<Long> roleExtIds, boolean isAdmin) {
        String sessionId = newSessionId();
        Instant now = Instant.now();
        UserSession session = new UserSession(
                sessionId,
                userExtId,
                username,
                globalName,
                avatarHash,
                Set.copyOf(roleExtIds),
                isAdmin,
                now,
                now.plus(Duration.ofSeconds(ttlSeconds)));
        sessions.put(sessionId, session);
        return session;
    }

    public Optional<UserSession> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        UserSession session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(session.expiresAt())) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public int activeSessionCount() {
        long now = Instant.now().toEpochMilli();
        int count = 0;
        for (UserSession session : sessions.values()) {
            if (session.expiresAt().toEpochMilli() > now) {
                count++;
            }
        }
        return count;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    private static String newSessionId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
