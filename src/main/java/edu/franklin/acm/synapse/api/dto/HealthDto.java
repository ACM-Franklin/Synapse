package edu.franklin.acm.synapse.api.dto;

public record HealthDto(String status) {
    public static HealthDto ok() {
        return new HealthDto("ok");
    }
}
