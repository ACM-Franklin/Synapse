package edu.franklin.acm.synapse.rules.engine.predicates;

import java.util.Set;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.franklin.acm.synapse.rules.engine.PredicateEvaluator;
import edu.franklin.acm.synapse.rules.engine.RuleContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Handles string match predicates including channel targeting,
 * message type matching, and attachment content type checks.
 *
 * Parameters vary by type:
 * - IN_CHANNEL: {"channel_ext_id": 123456789}
 * - CHANNEL_TYPE_IS: {"type": "TEXT"}
 * - MESSAGE_TYPE_IS: {"type": "0"}
 * - ATTACHMENT_EXTENSION_IS: {"extension": "png"}
 * - ATTACHMENT_CONTENT_TYPE_IS: {"content_type": "image/png"}
 * - ATTACHMENT_IS_IMAGE/VIDEO/AUDIO: no parameters
 */
@ApplicationScoped
public class StringMatchEvaluator implements PredicateEvaluator {

    private static final Logger log = Logger.getLogger(StringMatchEvaluator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> HANDLED = Set.of(
            "IN_CHANNEL", "NOT_IN_CHANNEL",
            "CHANNEL_TYPE_IS", "IN_CATEGORY",
            "MESSAGE_TYPE_IS",
            "ATTACHMENT_EXTENSION_IS", "ATTACHMENT_CONTENT_TYPE_IS",
            "ATTACHMENT_IS_IMAGE", "ATTACHMENT_IS_VIDEO", "ATTACHMENT_IS_AUDIO",
            "IN_VOICE_CHANNEL"
    );

    @Override
    public boolean handles(String predicateType) {
        return HANDLED.contains(predicateType);
    }

    @Override
    public boolean evaluate(String predicateType, RuleContext ctx, String parametersJson) {
        try {
            return switch (predicateType) {
                case "IN_CHANNEL" -> matchField(ctx, "channel_ext_id", parametersJson, "channel_ext_id", false);
                case "NOT_IN_CHANNEL" -> matchField(ctx, "channel_ext_id", parametersJson, "channel_ext_id", true);
                case "CHANNEL_TYPE_IS" -> matchField(ctx, "channel_type", parametersJson, "type", false);
                case "IN_CATEGORY" -> matchField(ctx, "category_ext_id", parametersJson, "category_ext_id", false);
                case "MESSAGE_TYPE_IS" -> matchField(ctx, "message_type", parametersJson, "type", false);
                case "IN_VOICE_CHANNEL" -> matchField(ctx, "voice_channel_ext_id", parametersJson, "channel_ext_id", false);
                case "ATTACHMENT_EXTENSION_IS" -> matchExtension(ctx, parametersJson);
                case "ATTACHMENT_CONTENT_TYPE_IS" -> matchContentType(ctx, parametersJson, false);
                case "ATTACHMENT_IS_IMAGE" -> prefixMatch(ctx, "image/");
                case "ATTACHMENT_IS_VIDEO" -> prefixMatch(ctx, "video/");
                case "ATTACHMENT_IS_AUDIO" -> prefixMatch(ctx, "audio/");
                default -> false;
            };
        } catch (JsonProcessingException e) {
            log.warnf(e, "StringMatchEvaluator failed for predicate '%s'", predicateType);
            return false;
        }
    }

    private boolean matchField(RuleContext ctx, String contextField,
                               String parametersJson, String paramKey, boolean negate) throws JsonProcessingException {
        if (parametersJson == null) return false;
        JsonNode params = MAPPER.readTree(parametersJson);
        // path() returns MissingNode (not null) when key is absent; asText() on MissingNode returns "".
        String expected = params.path(paramKey).asText();
        String actual = ctx.getStringField(contextField);
        if (actual == null) return negate;
        boolean matches = actual.equals(expected);
        return negate != matches;
    }

    private boolean matchExtension(RuleContext ctx, String parametersJson) throws JsonProcessingException {
        if (parametersJson == null) return false;
        String filename = ctx.attachmentFilename();
        if (filename == null) return false;
        JsonNode params = MAPPER.readTree(parametersJson);
        String extension = params.path("extension").asText().toLowerCase();
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        return filename.substring(dot + 1).toLowerCase().equals(extension);
    }

    private boolean matchContentType(RuleContext ctx, String parametersJson, boolean prefix) throws JsonProcessingException {
        if (parametersJson == null) return false;
        String contentType = ctx.attachmentContentType();
        if (contentType == null) return false;
        JsonNode params = MAPPER.readTree(parametersJson);
        String expected = params.path("content_type").asText();
        return prefix ? contentType.startsWith(expected) : contentType.equalsIgnoreCase(expected);
    }

    private boolean prefixMatch(RuleContext ctx, String prefix) {
        String contentType = ctx.attachmentContentType();
        return contentType != null && contentType.startsWith(prefix);
    }
}
