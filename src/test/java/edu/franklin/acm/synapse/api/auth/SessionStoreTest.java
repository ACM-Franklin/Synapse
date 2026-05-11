package edu.franklin.acm.synapse.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SessionStoreTest {

    @Test
    void createReturnsLookupableSessionWithStableIdAndRoles() {
        SessionStore store = new SessionStore(3600L);
        UserSession session = store.create(
                42L, "alice", "Alice", "abc", Set.of(100L, 200L), true);

        assertTrue(store.find(session.sessionId()).isPresent());
        UserSession found = store.find(session.sessionId()).orElseThrow();
        assertEquals(42L, found.userExtId());
        assertTrue(found.isAdmin());
        assertEquals(Set.of(100L, 200L), found.roleExtIds());
    }

    @Test
    void invalidateRemovesSession() {
        SessionStore store = new SessionStore(3600L);
        UserSession session = store.create(1L, "bob", null, null, Set.of(), false);
        store.invalidate(session.sessionId());
        assertFalse(store.find(session.sessionId()).isPresent());
    }

    @Test
    void expiredSessionIsNotReturned() throws Exception {
        SessionStore store = new SessionStore(0L); // immediate expiry
        UserSession session = store.create(1L, "carol", null, null, Set.of(), false);
        Thread.sleep(20);
        assertFalse(store.find(session.sessionId()).isPresent());
        assertEquals(0, store.activeSessionCount());
    }

    @Test
    void distinctCallsProduceDistinctSessionIds() {
        SessionStore store = new SessionStore(3600L);
        UserSession one = store.create(1L, "a", null, null, Set.of(), false);
        UserSession two = store.create(2L, "b", null, null, Set.of(), false);
        assertNotEquals(one.sessionId(), two.sessionId());
    }

    @Test
    void missingOrBlankSessionIdIsNotFound() {
        SessionStore store = new SessionStore(3600L);
        assertFalse(store.find(null).isPresent());
        assertFalse(store.find("").isPresent());
        assertFalse(store.find("does-not-exist").isPresent());
    }
}
