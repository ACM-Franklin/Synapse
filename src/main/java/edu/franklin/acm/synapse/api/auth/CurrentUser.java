package edu.franklin.acm.synapse.api.auth;

import java.util.Optional;

import jakarta.enterprise.context.RequestScoped;

/**
 * Per-request holder for the authenticated user's session, populated by
 * {@link AuthenticationFilter}. Empty if the request had no valid session.
 */
@RequestScoped
public class CurrentUser {

    private UserSession session;

    public void set(UserSession session) {
        this.session = session;
    }

    public Optional<UserSession> session() {
        return Optional.ofNullable(session);
    }

    public boolean isAuthenticated() {
        return session != null;
    }

    public boolean isAdmin() {
        return session != null && session.isAdmin();
    }

    public long userExtIdOrThrow() {
        if (session == null) {
            throw new IllegalStateException("No authenticated user");
        }
        return session.userExtId();
    }
}
