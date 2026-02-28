package edu.franklin.acm.synapse.rules.engine.predicates;

import java.util.Map;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.franklin.acm.synapse.rules.engine.PredicateEvaluator;
import edu.franklin.acm.synapse.rules.engine.RuleContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Handles all numeric threshold predicates. Each predicate type maps to a
 * field name, a default operator, and reads the threshold from parameters.
 *
 * Parameters: {"threshold": 80}
 * Some types also support: {"field": "content_length", "operator": ">=", "threshold": 80}
 */
@ApplicationScoped
public class NumericThresholdEvaluator implements PredicateEvaluator {

    private static final Logger log = Logger.getLogger(NumericThresholdEvaluator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("null") // Map.ofEntries() guarantees non-null values by contract
    private static final Map<String, NumericMapping> MAPPINGS = Map.ofEntries(
            Map.entry("MIN_CONTENT_LENGTH", new NumericMapping("content_length", ">=")),
            Map.entry("MAX_CONTENT_LENGTH", new NumericMapping("content_length", "<=")),
            Map.entry("MIN_ATTACHMENT_COUNT", new NumericMapping("attachment_count", ">=")),
            Map.entry("MIN_REACTION_COUNT", new NumericMapping("reaction_count", ">=")),
            Map.entry("MENTION_USER_COUNT_MAX", new NumericMapping("mention_user_count", "<=")),
            Map.entry("MEMBER_P_CURRENCY_MIN", new NumericMapping("p_currency", ">=")),
            Map.entry("MEMBER_P_CURRENCY_MAX", new NumericMapping("p_currency", "<=")),
            Map.entry("MEMBER_S_CURRENCY_MIN", new NumericMapping("s_currency", ">=")),
            Map.entry("MIN_EMBED_COUNT", new NumericMapping("embed_count", ">=")),
            Map.entry("MIN_SESSION_DURATION_MINUTES", new NumericMapping("session_duration_minutes", ">="))
    );

    @Override
    public boolean handles(String predicateType) {
        return MAPPINGS.containsKey(predicateType);
    }

    @Override
    public boolean evaluate(String predicateType, RuleContext ctx, String parametersJson) {
        NumericMapping mapping = MAPPINGS.get(predicateType);
        if (mapping == null) return false;
        if (parametersJson == null) return false;

        try {
            JsonNode params = MAPPER.readTree(parametersJson);

            String field = params.has("field") ? params.path("field").asText() : mapping.field;
            String operator = params.has("operator") ? params.path("operator").asText() : mapping.operator;
            double threshold = params.path("threshold").asDouble();

            Number actual = ctx.getNumericField(field);
            if (actual == null) return false;

            return compare(actual.doubleValue(), operator, threshold);
        } catch (JsonProcessingException e) {
            log.warnf(e, "NumericThresholdEvaluator failed for predicate '%s'", predicateType);
            return false;
        }
    }

    private boolean compare(double actual, String operator, double threshold) {
        return switch (operator) {
            case ">=" -> actual >= threshold;
            case "<=" -> actual <= threshold;
            case ">" -> actual > threshold;
            case "<" -> actual < threshold;
            case "==" -> actual == threshold;
            case "!=" -> actual != threshold;
            default -> false;
        };
    }

    private record NumericMapping(String field, String operator) {
    }
}
