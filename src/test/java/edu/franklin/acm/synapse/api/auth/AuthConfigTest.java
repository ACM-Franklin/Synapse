package edu.franklin.acm.synapse.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AuthConfigTest {

    @Test
    void adminRoleIdsParsesCommaSeparatedList() {
        AuthConfig cfg = new AuthConfig();
        cfg.adminRoleIdsRaw = Optional.of("100, 200,300");
        assertEquals(Set.of(100L, 200L, 300L), cfg.adminRoleIds());
    }

    @Test
    void adminRoleIdsIgnoresMalformedTokens() {
        AuthConfig cfg = new AuthConfig();
        cfg.adminRoleIdsRaw = Optional.of("100,not-a-snowflake,200");
        assertEquals(Set.of(100L, 200L), cfg.adminRoleIds());
    }

    @Test
    void adminRoleIdsReturnsEmptyForBlankConfig() {
        AuthConfig cfg = new AuthConfig();
        cfg.adminRoleIdsRaw = Optional.of("");
        assertTrue(cfg.adminRoleIds().isEmpty());
    }

    @Test
    void oauthConfiguredRequiresBothClientIdAndSecret() {
        AuthConfig cfg = new AuthConfig();
        cfg.clientId = "id";
        cfg.clientSecret = "";
        assertFalse(cfg.isOauthConfigured());

        cfg.clientSecret = "secret";
        assertTrue(cfg.isOauthConfigured());
    }
}
