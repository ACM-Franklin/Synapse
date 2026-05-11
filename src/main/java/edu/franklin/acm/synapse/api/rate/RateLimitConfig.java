package edu.franklin.acm.synapse.api.rate;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RateLimitConfig {

    @ConfigProperty(name = "synapse.api.rate-limit.enabled", defaultValue = "true")
    boolean enabled;
    @ConfigProperty(name = "synapse.api.rate-limit.window-seconds", defaultValue = "60")
    long windowSeconds;
    @ConfigProperty(name = "synapse.api.rate-limit.default-requests", defaultValue = "120")
    int defaultRequests;
    @ConfigProperty(name = "synapse.api.rate-limit.auth-requests", defaultValue = "20")
    int authRequests;
    @ConfigProperty(name = "synapse.api.rate-limit.admin-mutation-requests", defaultValue = "5")
    int adminMutationRequests;

    public boolean enabled() {
        return enabled;
    }

    public long windowSeconds() {
        return windowSeconds;
    }

    public int defaultRequests() {
        return defaultRequests;
    }

    public int authRequests() {
        return authRequests;
    }

    public int adminMutationRequests() {
        return adminMutationRequests;
    }
}