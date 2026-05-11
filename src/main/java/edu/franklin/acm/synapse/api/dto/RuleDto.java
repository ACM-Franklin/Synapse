package edu.franklin.acm.synapse.api.dto;

import java.util.List;

public record RuleDto(
        long id,
        String name,
        String description,
        String eventType,
        boolean enabled,
        boolean appliesLive,
        boolean appliesHistoric,
        int cooldownSeconds,
        boolean valid,
        List<String> invalidReasons,
        List<PredicateDto> predicates,
        List<OutcomeDto> outcomes,
        String createdAt,
        String updatedAt) {

    public record PredicateDto(
            long id,
            String predicateType,
            String parameters,
            int sortOrder) {
    }

    public record OutcomeDto(
            long id,
            String type,
            Integer pCurrency,
            Integer sCurrency,
            String parameters) {
    }
}
