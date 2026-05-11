package edu.franklin.acm.synapse.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class OAuthStateStoreTest {

    @Test
    void issuedStateCanBeConsumedExactlyOnce() {
        OAuthStateStore store = new OAuthStateStore(300L);
        OAuthStateStore.OAuthState state = store.issue();

        assertTrue(store.consume(state.value()).isPresent());
        assertFalse(store.consume(state.value()).isPresent());
        assertEquals(0, store.activeStateCount());
    }

    @Test
    void distinctStatesAreGenerated() {
        OAuthStateStore store = new OAuthStateStore(300L);

        assertNotEquals(store.issue().value(), store.issue().value());
    }

    @Test
    void blankOrUnknownStateIsRejected() {
        OAuthStateStore store = new OAuthStateStore(300L);

        assertFalse(store.consume(null).isPresent());
        assertFalse(store.consume("").isPresent());
        assertFalse(store.consume("not-issued").isPresent());
    }

    @Test
    void issuedStateCarriesPkceVerifierAndChallenge() {
        OAuthStateStore store = new OAuthStateStore(300L);
        OAuthStateStore.OAuthState state = store.issue();

        assertTrue(state.codeVerifier().length() >= 43);
        assertTrue(state.codeChallenge().length() >= 43);
        assertNotEquals(state.codeVerifier(), state.codeChallenge());
        assertEquals(state.codeVerifier(), store.consume(state.value()).orElseThrow().codeVerifier());
    }

    @Test
    void stateStoreEvictsOldestEntriesWhenCapacityIsReached() {
        OAuthStateStore store = new OAuthStateStore(300L, 1);
        OAuthStateStore.OAuthState first = store.issue();
        OAuthStateStore.OAuthState second = store.issue();

        assertFalse(store.consume(first.value()).isPresent());
        assertTrue(store.consume(second.value()).isPresent());
        assertEquals(0, store.activeStateCount());
    }

    @Test
    void timingSafeComparisonRequiresExactMatch() {
        OAuthStateStore store = new OAuthStateStore(300L);
        OAuthStateStore.OAuthState state = store.issue();

        assertTrue(OAuthStateStore.timingSafeEquals(state.value(), state.value()));
        assertFalse(OAuthStateStore.timingSafeEquals(state.value(), state.value() + "x"));
        assertFalse(OAuthStateStore.timingSafeEquals(state.value(), null));
    }
}