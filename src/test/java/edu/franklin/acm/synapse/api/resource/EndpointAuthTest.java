package edu.franklin.acm.synapse.api.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Smoke test for the deployed JAX-RS resources. Confirms health is public,
 * non-health routes reject unauthenticated callers, and admin routes
 * additionally reject confirmation-less POSTs.
 *
 * <p>Lives next to the resources under test rather than in a generic e2e
 * package because failure here is an auth-wiring regression, not a feature
 * regression. SynapseBot is excluded from CDI in the test profile so JDA
 * does not attempt to connect.
 */
@QuarkusTest
class EndpointAuthTest {

    @Test
    void healthIsPublic() {
        given().when().get("/api/health")
                .then().statusCode(200)
                .body("status", equalTo("ok"));
    }

    @Test
    void currentUserReturns401Unauthenticated() {
        given().when().get("/api/auth/me").then().statusCode(401);
    }

    @Test
    void guildSummaryRequiresAuth() {
        given().when().get("/api/guild/summary").then().statusCode(401);
    }

    @Test
    void leaderboardRequiresAuth() {
        given().when().get("/api/leaderboard").then().statusCode(401);
    }

    @Test
    void memberDashboardRequiresAuth() {
        given().when().get("/api/members/me/dashboard").then().statusCode(401);
    }

    @Test
    void rulesListRequiresAuth() {
        given().when().get("/api/rules").then().statusCode(401);
    }

    @Test
    void systemStatusRequiresAuth() {
        given().when().get("/api/system/status").then().statusCode(401);
    }

    @Test
    void historicalScanStartRequiresAuth() {
        given().contentType("application/json")
                .body("{\"confirm\":\"I_UNDERSTAND_THIS_SCANS_DISCORD\"}")
                .when().post("/api/scans/historical")
                .then().statusCode(401);
    }

    @Test
    void replayRequiresAuth() {
        given().contentType("application/json")
                .body("{\"confirm\":\"I_UNDERSTAND_THIS_REPLAYS_REWARDS\"}")
                .when().post("/api/admin/replay/messages")
                .then().statusCode(401);
    }
}
