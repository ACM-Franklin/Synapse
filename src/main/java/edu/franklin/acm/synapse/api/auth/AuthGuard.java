package edu.franklin.acm.synapse.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Authorization helper. Resources call {@link #requireMember()} or
 * {@link #requireAdmin()} to enforce access. Throwing {@link WebApplicationException}
 * keeps responses consistent without dragging in custom annotations.
 */
@ApplicationScoped
public class AuthGuard {

    @Inject CurrentUser currentUser;

    public UserSession requireMember() {
        return currentUser.session().orElseThrow(() ->
                new WebApplicationException("Authentication required", Response.Status.UNAUTHORIZED));
    }

    public UserSession requireAdmin() {
        UserSession session = requireMember();
        if (!session.isAdmin()) {
            throw new WebApplicationException("Admin role required", Response.Status.FORBIDDEN);
        }
        return session;
    }
}
