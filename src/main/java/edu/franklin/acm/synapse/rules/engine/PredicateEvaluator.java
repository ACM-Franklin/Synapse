package edu.franklin.acm.synapse.rules.engine;

/**
 * A parameterized predicate type. Each implementation handles one structural
 * pattern (boolean field check, numeric threshold, string match, etc.) and
 * maps multiple predicate type names to the appropriate field accessors.
 */
public interface PredicateEvaluator {

    /**
     * Returns true if this evaluator handles the given predicate type name.
     */
    boolean handles(String predicateType);

    /**
     * Evaluate the predicate against the given context.
     *
     * @param predicateType the specific predicate type being evaluated
     * @param ctx           the rule evaluation context
     * @param parametersJson JSON parameters for this predicate instance, or null
     * @return true if the predicate passes
     */
    boolean evaluate(String predicateType, RuleContext ctx, String parametersJson);
}
