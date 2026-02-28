package edu.franklin.acm.synapse.activity.rules;

public record RulePredicate(
        long id,
        long ruleId,
        String predicateType,
        String parameters,
        int sortOrder) {
}
