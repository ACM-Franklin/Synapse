package edu.franklin.acm.synapse.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

class AuthGuardTest {

    @Test
    void requireMemberThrowsUnauthorizedWhenNoSession() {
        AuthGuard guard = newGuard(null);
        WebApplicationException ex = assertThrows(WebApplicationException.class, guard::requireMember);
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void requireMemberReturnsSessionForAuthenticatedUser() {
        UserSession session = newSession(false);
        AuthGuard guard = newGuard(session);
        assertSame(session, guard.requireMember());
    }

    @Test
    void requireAdminThrowsForbiddenForNonAdmin() {
        AuthGuard guard = newGuard(newSession(false));
        WebApplicationException ex = assertThrows(WebApplicationException.class, guard::requireAdmin);
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void requireAdminReturnsSessionForAdmin() {
        UserSession session = newSession(true);
        AuthGuard guard = newGuard(session);
        assertSame(session, guard.requireAdmin());
    }

    private static AuthGuard newGuard(UserSession session) {
        CurrentUser cu = new CurrentUser();
        if (session != null) {
            cu.set(session);
        }
        AuthGuard guard = new AuthGuard();
        guard.currentUser = cu;
        return guard;
    }

    private static UserSession newSession(boolean admin) {
        return new UserSession(
                "sid",
                1L,
                "name",
                "Name",
                null,
                Set.of(),
                admin,
                Instant.now(),
                Instant.now().plusSeconds(3600));
    }
}
