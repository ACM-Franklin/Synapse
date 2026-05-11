package edu.franklin.acm.synapse.api.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;

/**
 * Resolves the session cookie into a {@link CurrentUser}, if present. Does not
 * itself reject unauthenticated requests — endpoints that require auth call
 * {@link AuthGuard} explicitly. This keeps health and login routes public
 * without per-resource exemption ceremony.
 */
@Provider
public class AuthenticationFilter implements ContainerRequestFilter {

    @Inject SessionStore sessions;
    @Inject CurrentUser currentUser;
    @Inject AuthConfig config;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Cookie cookie = requestContext.getCookies().get(config.cookieName());
        if (cookie == null) {
            return;
        }
        sessions.find(cookie.getValue()).ifPresent(currentUser::set);
    }
}
