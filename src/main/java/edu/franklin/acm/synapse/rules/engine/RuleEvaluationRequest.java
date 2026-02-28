package edu.franklin.acm.synapse.rules.engine;

/**
 * CDI async event payload fired by the live scanner after persisting an event.
 * The rule engine observes this asynchronously to evaluate matching rules.
 */
public record RuleEvaluationRequest(RuleContext context) {
}
