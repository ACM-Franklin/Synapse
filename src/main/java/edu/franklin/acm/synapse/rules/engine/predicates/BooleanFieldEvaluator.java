package edu.franklin.acm.synapse.rules.engine.predicates;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.franklin.acm.synapse.rules.engine.PredicateEvaluator;
import edu.franklin.acm.synapse.rules.engine.RuleContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Handles all boolean field predicates. Each predicate type maps to a
 * specific boolean field on the RuleContext and an expected value.
 */
@ApplicationScoped
public class BooleanFieldEvaluator implements PredicateEvaluator {

    private static final Logger log = Logger.getLogger(BooleanFieldEvaluator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("null") // Map.ofEntries() guarantees non-null values by contract
    private static final Map<String, BooleanMapping> MAPPINGS = Map.ofEntries(
            Map.entry("AUTHOR_NOT_BOT", new BooleanMapping("author_is_bot", false)),
            Map.entry("IS_REPLY", new BooleanMapping("is_reply", true)),
            Map.entry("IS_NOT_REPLY", new BooleanMapping("is_reply", false)),
            Map.entry("HAS_ATTACHMENT", new BooleanMapping("has_attachments", true)),
            Map.entry("NO_ATTACHMENT", new BooleanMapping("has_attachments", false)),
            Map.entry("IS_NOT_TTS", new BooleanMapping("is_tts", false)),
            Map.entry("HAS_EMBED", new BooleanMapping("has_embed", true)),
            Map.entry("HAS_POLL", new BooleanMapping("has_poll", true)),
            Map.entry("HAS_STICKER", new BooleanMapping("has_stickers", true)),
            Map.entry("IS_VOICE_MESSAGE", new BooleanMapping("is_voice_message", true)),
            Map.entry("NOT_MENTIONS_EVERYONE", new BooleanMapping("mention_everyone", false)),
            Map.entry("MEMBER_IS_BOOSTING", new BooleanMapping("member_is_boosting", true)),
            Map.entry("IS_PINNED", new BooleanMapping("is_pinned", true))
    );

    @Override
    public boolean handles(String predicateType) {
        return MAPPINGS.containsKey(predicateType);
    }

    @Override
    public boolean evaluate(String predicateType, RuleContext ctx, String parametersJson) {
        BooleanMapping mapping = MAPPINGS.get(predicateType);
        if (mapping == null) return false;

        boolean expected = mapping.expected;

        // Allow overriding expected value via parameters
        if (parametersJson != null) {
            try {
                var params = MAPPER.readTree(parametersJson);
                if (params.has("expected")) {
                    expected = params.get("expected").asBoolean();
                }
            } catch (JsonProcessingException e) {
                log.warnf(e, "BooleanFieldEvaluator could not parse parameters for predicate '%s'", predicateType);
            }
        }

        Boolean actual = ctx.getBooleanField(mapping.field);
        if (actual == null) return false;
        return actual == expected;
    }

    private record BooleanMapping(String field, boolean expected) {
    }
}
