package edu.franklin.acm.synapse.api.rate;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

class RateLimitFilterTest {

    @Test
    void healthIsNotRateLimited() {
        RateLimitFilter filter = filter(1, 1, 1);
        ContainerRequestContext request = request("GET", "api/health");

        filter.filter(request);
        filter.filter(request);

        verify(request, never()).abortWith(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authRoutesUseAuthLimit() {
        RateLimitFilter filter = filter(100, 1, 100);
        ContainerRequestContext request = request("GET", "api/auth/login");

        filter.filter(request);
        filter.filter(request);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(request).abortWith(response.capture());
        assertEquals(429, response.getValue().getStatus());
    }

    @Test
    void adminMutationsUseAdminLimit() {
        RateLimitFilter filter = filter(100, 100, 1);
        ContainerRequestContext request = request("POST", "api/admin/replay/messages");

        filter.filter(request);
        filter.filter(request);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(request).abortWith(response.capture());
        assertEquals(429, response.getValue().getStatus());
    }

    private static RateLimitFilter filter(int defaultRequests, int authRequests, int adminRequests) {
        RateLimitFilter filter = new RateLimitFilter();
        TestRateLimitConfig config = new TestRateLimitConfig();
        config.enabled = true;
        config.windowSeconds = 60;
        config.defaultRequests = defaultRequests;
        config.authRequests = authRequests;
        config.adminMutationRequests = adminRequests;
        filter.config = config;
        filter.clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        return filter;
    }

    private static ContainerRequestContext request(String method, String path) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getHeaderString("X-Forwarded-For")).thenReturn("127.0.0.1");
        when(uriInfo.getPath()).thenReturn(path);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost:8080/" + path));
        return request;
    }

    private static final class TestRateLimitConfig extends RateLimitConfig {
    }
}