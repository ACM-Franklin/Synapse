package edu.franklin.acm.synapse.api.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;

class DiscordOAuthClientTest {

    @Test
    void authorizeUrlIncludesPkceChallenge() {
        CapturingClient client = client();

        String url = client.buildAuthorizeUrl("state-value", "challenge-value");

        assertTrue(url.contains("state=state-value"));
        assertTrue(url.contains("code_challenge=challenge-value"));
        assertTrue(url.contains("code_challenge_method=S256"));
        assertTrue(url.contains("guilds.members.read"));
    }

    @Test
    void tokenExchangeSendsPkceVerifier() {
        CapturingClient client = client();

        client.exchangeCodeForAccessToken("code-value", "verifier-value");

        String body = body(client.lastRequest);
        assertTrue(body.contains("code=code-value"));
        assertTrue(body.contains("code_verifier=verifier-value"));
        assertTrue(body.contains("client_secret=secret"));
    }

    private static CapturingClient client() {
        CapturingClient client = new CapturingClient();
        client.config = new FakeAuthConfig();
        client.init();
        return client;
    }

    private static String body(HttpRequest request) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }
            @Override public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                output.writeBytes(chunk);
            }
            @Override public void onError(Throwable throwable) {
                done.countDown();
            }
            @Override public void onComplete() {
                done.countDown();
            }
        });
        try {
            if (!done.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out reading request body");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted reading request body", e);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static final class CapturingClient extends DiscordOAuthClient {
        private HttpRequest lastRequest;

        @Override
        protected HttpResponse<String> send(HttpRequest req) {
            lastRequest = req;
            return new StringResponse(req, 200, "{\"access_token\":\"token\"}");
        }
    }

    private record StringResponse(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (left, right) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private static final class FakeAuthConfig extends AuthConfig {
        @Override public String clientId() { return "client"; }
        @Override public String clientSecret() { return "secret"; }
        @Override public String redirectUri() { return "http://localhost:8080/api/auth/callback"; }
        @Override public String authorizeUrl() { return "https://discord.example/oauth2/authorize"; }
        @Override public String tokenUrl() { return "https://discord.example/oauth2/token"; }
        @Override public String apiBaseUrl() { return "https://discord.example/api"; }
        @Override public Set<Long> adminRoleIds() { return Set.of(); }
    }
}