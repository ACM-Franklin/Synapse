package edu.franklin.acm.synapse.api.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OAuthStateStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ConcurrentHashMap<String, Instant> states = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public OAuthStateStore(@ConfigProperty(name = "synapse.auth.oauth.state-ttl-seconds", defaultValue = "300") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public OAuthState issue() {
        purgeExpired();
        String state = newState();
        Instant expiresAt = Instant.now().plus(Duration.ofSeconds(ttlSeconds));
        states.put(state, expiresAt);
        return new OAuthState(state, expiresAt);
    }

    public boolean consume(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        Instant expiresAt = states.remove(state);
        return expiresAt != null && !Instant.now().isAfter(expiresAt);
    }

    public int activeStateCount() {
        purgeExpired();
        return states.size();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }

    private static String newState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public record OAuthState(String value, Instant expiresAt) {
    }
}