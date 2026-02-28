package edu.franklin.acm.synapse.activity.rules;

public record RuleOutcome(
        long id,
        long ruleId,
        String type,
        Integer pCurrency,
        Integer sCurrency,
        String parameters) {
}
