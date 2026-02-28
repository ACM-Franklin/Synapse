package edu.franklin.acm.synapse.activity.rules;

public record RuleEvaluation(
        long id,
        long ruleId,
        long eventId,
        long memberId,
        String createdAt) {
}
