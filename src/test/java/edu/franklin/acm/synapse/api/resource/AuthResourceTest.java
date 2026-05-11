package edu.franklin.acm.synapse.api.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.franklin.acm.synapse.api.auth.AuthConfig;
import edu.franklin.acm.synapse.api.auth.AuthService;
import edu.franklin.acm.synapse.api.auth.CurrentUser;
import edu.franklin.acm.synapse.api.auth.OAuthStateStore;
import edu.franklin.acm.synapse.api.auth.SessionStore;
import edu.franklin.acm.synapse.api.auth.UserSession;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

class AuthResourceTest {

    @Test
    void loginIssuesStateCookieAndBuildsAuthorizeUrlWithGeneratedState() {
        AuthService authService = mock(AuthService.class);
        when(authService.buildAuthorizeUrl(anyString())).thenAnswer(invocation ->
                "https://discord.example/auth?state=" + invocation.getArgument(0));
        AuthResource resource = resource(authService, new OAuthStateStore(300L));

        Response response = resource.login();
        NewCookie stateCookie = response.getCookies().get("synapse_oauth_state");
        AuthResource.LoginResponse body = (AuthResource.LoginResponse) response.getEntity();

        assertEquals(200, response.getStatus());
        assertTrue(stateCookie.isHttpOnly());
        assertTrue(body.authorizeUrl().contains("state=" + stateCookie.getValue()));
    }

    @Test
    void callbackRejectsMismatchedStateBeforeCompletingLogin() {
        AuthService authService = mock(AuthService.class);
        OAuthStateStore stateStore = new OAuthStateStore(300L);
        OAuthStateStore.OAuthState state = stateStore.issue();
        AuthResource resource = resource(authService, stateStore);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.callback("code", state.value(), headers("wrong-state")));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
        verify(authService, never()).completeLogin(anyString());
    }

    @Test
    void callbackConsumesStateAndSetsSessionCookie() {
        AuthService authService = mock(AuthService.class);
        OAuthStateStore stateStore = new OAuthStateStore(300L);
        OAuthStateStore.OAuthState state = stateStore.issue();
        when(authService.completeLogin("code")).thenReturn(session());
        AuthResource resource = resource(authService, stateStore);

        Response response = resource.callback("code", state.value(), headers(state.value()));

        assertEquals(303, response.getStatus());
        assertEquals("sid", response.getCookies().get("synapse_session").getValue());
        assertEquals(0, response.getCookies().get("synapse_oauth_state").getMaxAge());
        WebApplicationException reused = assertThrows(WebApplicationException.class,
            () -> resource.callback("code", state.value(), headers(state.value())));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), reused.getResponse().getStatus());
    }

    private static AuthResource resource(AuthService authService, OAuthStateStore stateStore) {
        AuthResource resource = new AuthResource();
        resource.authService = authService;
        resource.config = new TestAuthConfig();
        resource.currentUser = new CurrentUser();
        resource.sessions = new SessionStore(3600L);
        resource.oauthStates = stateStore;
        return resource;
    }

    private static HttpHeaders headers(String state) {
        HttpHeaders headers = mock(HttpHeaders.class);
        Cookie cookie = new Cookie.Builder("synapse_oauth_state").value(state).build();
        when(headers.getCookies()).thenReturn(Map.of("synapse_oauth_state", cookie));
        return headers;
    }

    private static UserSession session() {
        return new UserSession(
                "sid",
                1L,
                "name",
                "Name",
                null,
                Set.of(),
                false,
                Instant.now(),
                Instant.now().plusSeconds(3600));
    }

    private static final class TestAuthConfig extends AuthConfig {
        @Override public String cookieName() { return "synapse_session"; }
        @Override public String oauthStateCookieName() { return "synapse_oauth_state"; }
        @Override public long oauthStateTtlSeconds() { return 300L; }
        @Override public long sessionTtlSeconds() { return 3600L; }
        @Override public boolean cookieSecure() { return false; }
        @Override public String frontendRedirectUri() { return "http://localhost:5173/"; }
    }
}