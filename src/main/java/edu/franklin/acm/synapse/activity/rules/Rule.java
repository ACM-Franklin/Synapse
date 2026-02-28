package edu.franklin.acm.synapse.activity.rules;

public record Rule(
        long id,
        String name,
        String description,
        String eventType,
        boolean enabled,
        boolean appliesLive,
        boolean appliesHistoric,
        int cooldownSeconds,
        String createdAt,
        String updatedAt) {
}
