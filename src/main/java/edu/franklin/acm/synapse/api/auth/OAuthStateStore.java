package edu.franklin.acm.synapse.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OAuthStateStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ConcurrentHashMap<String, OAuthState> states = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> insertionOrder = new ConcurrentLinkedQueue<>();
    private final long ttlSeconds;
    private final int maxActiveStates;

    @Inject
    public OAuthStateStore(
            @ConfigProperty(name = "synapse.auth.oauth.state-ttl-seconds", defaultValue = "300") long ttlSeconds,
            @ConfigProperty(name = "synapse.auth.oauth.state-max-active", defaultValue = "4096") int maxActiveStates) {
        this.ttlSeconds = ttlSeconds;
        this.maxActiveStates = Math.max(1, maxActiveStates);
    }

    public OAuthStateStore(long ttlSeconds) {
        this(ttlSeconds, 4096);
    }

    public OAuthState issue() {
        purgeExpired();
        evictOverflow();
        String state = newState();
        String codeVerifier = newCodeVerifier();
        Instant expiresAt = Instant.now().plus(Duration.ofSeconds(ttlSeconds));
        OAuthState oauthState = new OAuthState(state, expiresAt, codeVerifier, codeChallenge(codeVerifier));
        states.put(state, oauthState);
        insertionOrder.add(state);
        return oauthState;
    }

    public Optional<OAuthState> consume(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        OAuthState oauthState = states.remove(state);
        if (oauthState == null || Instant.now().isAfter(oauthState.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(oauthState);
    }

    public int activeStateCount() {
        purgeExpired();
        return states.size();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    private void evictOverflow() {
        while (states.size() >= maxActiveStates) {
            String oldestState = insertionOrder.poll();
            if (oldestState == null) {
                return;
            }
            states.remove(oldestState);
        }
    }

    private static String newState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private static String newCodeVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private static String codeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return ENCODER.encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for OAuth PKCE", e);
        }
    }

    public static boolean timingSafeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    public record OAuthState(String value, Instant expiresAt, String codeVerifier, String codeChallenge) {
    }
}