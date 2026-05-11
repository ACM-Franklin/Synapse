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

        assertTrue(store.consume(state.value()));
        assertFalse(store.consume(state.value()));
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

        assertFalse(store.consume(null));
        assertFalse(store.consume(""));
        assertFalse(store.consume("not-issued"));
    }
}