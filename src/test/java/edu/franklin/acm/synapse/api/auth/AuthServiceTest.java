package edu.franklin.acm.synapse.api.auth;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

class AuthServiceTest {

    private static final long GUILD_ID = 99L;
    private static final long ADMIN_ROLE = 500L;

    @Test
    void completeLoginRejectsBotAccounts() {
        AuthService service = serviceWith(adminRoles());
        DiscordOAuthClient client = service.discord;

        when(client.exchangeCodeForAccessToken(anyString(), anyString())).thenReturn("tok");
        when(client.fetchIdentity(anyString())).thenReturn(
                new DiscordOAuthClient.DiscordUser(1L, "botboy", null, null, true));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.completeLogin("code", "verifier"));
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void completeLoginRejectsNonGuildMembers() {
        AuthService service = serviceWith(adminRoles());
        DiscordOAuthClient client = service.discord;

        when(client.exchangeCodeForAccessToken(anyString(), anyString())).thenReturn("tok");
        when(client.fetchIdentity(anyString())).thenReturn(
                new DiscordOAuthClient.DiscordUser(1L, "outsider", null, null, false));
        when(client.fetchGuildMember(anyString(), anyLong())).thenReturn(null);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.completeLogin("code", "verifier"));
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void completeLoginMintsSessionWithAdminFlagWhenRoleMatches() {
        AuthService service = serviceWith(adminRoles());
        DiscordOAuthClient client = service.discord;

        when(client.exchangeCodeForAccessToken(anyString(), anyString())).thenReturn("tok");
        when(client.fetchIdentity(anyString())).thenReturn(
                new DiscordOAuthClient.DiscordUser(7L, "admin-alice", "Alice", "hash", false));
        when(client.fetchGuildMember(anyString(), anyLong())).thenReturn(
                new DiscordOAuthClient.DiscordGuildMember("nick", Set.of(ADMIN_ROLE, 999L)));

        UserSession session = service.completeLogin("code", "verifier");
        assertTrue(session.isAdmin());
        assertEquals(7L, session.userExtId());
        assertTrue(session.roleExtIds().contains(ADMIN_ROLE));
        verify(client).exchangeCodeForAccessToken(eq("code"), eq("verifier"));
    }

    @Test
    void completeLoginMintsMemberSessionWhenRoleDoesNotMatch() {
        AuthService service = serviceWith(adminRoles());
        DiscordOAuthClient client = service.discord;

        when(client.exchangeCodeForAccessToken(anyString(), anyString())).thenReturn("tok");
        when(client.fetchIdentity(anyString())).thenReturn(
                new DiscordOAuthClient.DiscordUser(8L, "plain-bob", null, null, false));
        when(client.fetchGuildMember(anyString(), anyLong())).thenReturn(
                new DiscordOAuthClient.DiscordGuildMember(null, Set.of(123L)));

        UserSession session = service.completeLogin("code", "verifier");
        assertFalse(session.isAdmin());
        assertEquals(8L, session.userExtId());
    }

    @Test
    void completeLoginFailsWhenOauthNotConfigured() {
        AuthService service = serviceWith(adminRoles(), false, GUILD_ID);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.completeLogin("code", "verifier"));
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void completeLoginFailsWhenGuildIdNotConfigured() {
        AuthService service = serviceWith(adminRoles(), true, 0L);
        DiscordOAuthClient client = service.discord;
        when(client.exchangeCodeForAccessToken(anyString(), anyString())).thenReturn("tok");
        when(client.fetchIdentity(anyString())).thenReturn(
                new DiscordOAuthClient.DiscordUser(1L, "u", null, null, false));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.completeLogin("code", "verifier"));
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), ex.getResponse().getStatus());
    }

    private static Set<Long> adminRoles() {
        return Set.of(ADMIN_ROLE);
    }

    private static AuthService serviceWith(Set<Long> adminRoles) {
        return serviceWith(adminRoles, true, GUILD_ID);
    }

    private static AuthService serviceWith(Set<Long> adminRoles, boolean oauthConfigured, long guildId) {
        AuthService service = new AuthService();
        service.discord = Mockito.mock(DiscordOAuthClient.class);
        service.sessions = new SessionStore(3600L);
        service.config = new FakeAuthConfig(adminRoles, oauthConfigured, guildId);
        return service;
    }

    private static final class FakeAuthConfig extends AuthConfig {
        private final Set<Long> adminRoles;
        private final boolean oauthConfigured;
        private final long configuredGuildId;

        FakeAuthConfig(Set<Long> adminRoles, boolean oauthConfigured, long guildId) {
            this.adminRoles = adminRoles;
            this.oauthConfigured = oauthConfigured;
            this.configuredGuildId = guildId;
        }

        @Override public boolean isOauthConfigured() { return oauthConfigured; }
        @Override public Set<Long> adminRoleIds() { return adminRoles; }
        @Override public long guildId() { return configuredGuildId; }
        @Override public String clientId() { return "client"; }
        @Override public String clientSecret() { return "secret"; }
        @Override public String redirectUri() { return "http://localhost/callback"; }
        @Override public String frontendRedirectUri() { return "http://localhost:5173/"; }
        @Override public String authorizeUrl() { return "https://discord/auth"; }
        @Override public String tokenUrl() { return "https://discord/token"; }
        @Override public String apiBaseUrl() { return "https://discord/api"; }
        @Override public String cookieName() { return "synapse_session"; }
        @Override public boolean cookieSecure() { return false; }
        @Override public long sessionTtlSeconds() { return 3600L; }
    }
}
