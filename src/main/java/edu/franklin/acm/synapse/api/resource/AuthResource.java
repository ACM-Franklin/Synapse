package edu.franklin.acm.synapse.api.resource;

import java.net.URI;

import edu.franklin.acm.synapse.api.auth.AuthConfig;
import edu.franklin.acm.synapse.api.auth.AuthService;
import edu.franklin.acm.synapse.api.auth.CurrentUser;
import edu.franklin.acm.synapse.api.auth.OAuthStateStore;
import edu.franklin.acm.synapse.api.auth.SessionStore;
import edu.franklin.acm.synapse.api.auth.UserSession;
import edu.franklin.acm.synapse.api.dto.CurrentUserDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Context;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject AuthService authService;
    @Inject AuthConfig config;
    @Inject CurrentUser currentUser;
    @Inject SessionStore sessions;
    @Inject OAuthStateStore oauthStates;

    /**
     * Returns the authorize URL the frontend should redirect the user to.
     * Returned as JSON rather than a 302 so the frontend can render its own
     * "Sign in with Discord" button.
     */
    @GET
    @Path("/login")
    public Response login() {
        OAuthStateStore.OAuthState state = oauthStates.issue();
        String url = authService.buildAuthorizeUrl(state.value());
        return Response.ok(new LoginResponse(url))
                .cookie(stateCookie(state.value(), (int) config.oauthStateTtlSeconds()))
                .build();
    }

    /**
     * OAuth2 redirect target. Mints a session, sets the session cookie, and
     * redirects the browser back to the configured frontend.
     */
    @GET
    @Path("/callback")
    public Response callback(@QueryParam("code") String code,
                             @QueryParam("state") String state,
                             @Context HttpHeaders headers) {
        if (code == null || code.isBlank()) {
            throw new WebApplicationException("Missing OAuth code", Response.Status.BAD_REQUEST);
        }
        String cookieState = cookieValue(headers, config.oauthStateCookieName());
        if (state == null || state.isBlank()
                || cookieState == null
                || !state.equals(cookieState)
                || !oauthStates.consume(state)) {
            throw new WebApplicationException("Invalid OAuth state", Response.Status.BAD_REQUEST);
        }
        UserSession session = authService.completeLogin(code);
        NewCookie cookie = sessionCookie(session.sessionId(), (int) config.sessionTtlSeconds());
        return Response.seeOther(URI.create(config.frontendRedirectUri()))
                .cookie(cookie)
                .cookie(stateCookie("", 0))
                .build();
    }

    @GET
    @Path("/me")
    public Response me() {
        return currentUser.session()
                .map(s -> Response.ok(new CurrentUserDto(
                        String.valueOf(s.userExtId()),
                        s.username(),
                        s.globalName(),
                        s.avatarHash(),
                        true,
                        s.isAdmin())).build())
                .orElseGet(() -> Response.status(Response.Status.UNAUTHORIZED).build());
    }

    @POST
    @Path("/logout")
    public Response logout(@Context HttpHeaders headers) {
        String cookieValue = cookieValue(headers, config.cookieName());
        if (cookieValue != null) {
            sessions.invalidate(cookieValue);
        }
        NewCookie expired = sessionCookie("", 0);
        return Response.noContent().cookie(expired).build();
    }

    private NewCookie sessionCookie(String value, int maxAge) {
        return new NewCookie.Builder(config.cookieName())
                .value(value)
                .path("/")
                .httpOnly(true)
                .secure(config.cookieSecure())
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(maxAge)
                .build();
    }

    private NewCookie stateCookie(String value, int maxAge) {
        return new NewCookie.Builder(config.oauthStateCookieName())
                .value(value)
                .path("/api/auth")
                .httpOnly(true)
                .secure(config.cookieSecure())
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(maxAge)
                .build();
    }

    private static String cookieValue(HttpHeaders headers, String cookieName) {
        if (headers == null || cookieName == null) {
            return null;
        }
        Cookie cookie = headers.getCookies().get(cookieName);
        return cookie == null ? null : cookie.getValue();
    }

    public record LoginResponse(String authorizeUrl) {
    }
}
