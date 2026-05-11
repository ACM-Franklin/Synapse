package edu.franklin.acm.synapse.api.rate;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHENTICATION - 10)
public class RateLimitFilter implements ContainerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Inject RateLimitConfig config;
    Clock clock = Clock.systemUTC();

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (config == null || !config.enabled()) {
            return;
        }
        String method = requestContext.getMethod().toUpperCase(Locale.ROOT);
        String path = requestContext.getUriInfo().getPath();
        if ("api/health".equals(path)) {
            return;
        }

        LimitPolicy policy = policyFor(method, path);
        String key = clientKey(requestContext) + '|' + method + '|' + normalizePath(path) + '|' + policy.limit();
        long now = clock.millis();
        long windowMillis = Math.max(1L, config.windowSeconds()) * 1_000L;
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now));

        long retryAfterSeconds;
        synchronized (bucket) {
            if (now - bucket.windowStartedAt >= windowMillis) {
                bucket.windowStartedAt = now;
                bucket.count = 0;
            }
            if (bucket.count >= policy.limit()) {
                retryAfterSeconds = Math.max(1L, (bucket.windowStartedAt + windowMillis - now + 999L) / 1_000L);
            } else {
                bucket.count++;
                return;
            }
        }

        requestContext.abortWith(Response.status(429)
                .header("Retry-After", Long.toString(retryAfterSeconds))
                .entity(new RateLimitResponse("rate_limited", retryAfterSeconds))
                .build());
    }

    private LimitPolicy policyFor(String method, String path) {
        if (path.startsWith("api/auth/login") || path.startsWith("api/auth/callback")) {
            return new LimitPolicy(config.authRequests());
        }
        if ("POST".equals(method)
                && (path.equals("api/scans/historical") || path.equals("api/admin/replay/messages"))) {
            return new LimitPolicy(config.adminMutationRequests());
        }
        return new LimitPolicy(config.defaultRequests());
    }

    private static String clientKey(ContainerRequestContext requestContext) {
        String forwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        String realIp = requestContext.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return "unknown";
    }

    private static String normalizePath(String path) {
        return path.replaceAll("/\\d+", "/{id}");
    }

    private record LimitPolicy(int limit) {
    }

    private record RateLimitResponse(String error, long retryAfterSeconds) {
    }

    private static final class Bucket {
        private long windowStartedAt;
        private int count;

        private Bucket(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}