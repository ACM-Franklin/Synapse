package edu.franklin.acm.synapse.api.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import edu.franklin.acm.synapse.activity.member.RoleDao;
import edu.franklin.acm.synapse.activity.rules.Rule;
import edu.franklin.acm.synapse.activity.rules.RulePredicate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Validates rule references against current schema state.
 *
 * <p>Honest scope: predicate types like {@code IN_CHANNEL}, {@code IN_CATEGORY},
 * {@code IN_VOICE_CHANNEL}, {@code MEMBER_HAS_ROLE}, etc. store a {@code channel_ext_id},
 * {@code category_ext_id}, or {@code role_ext_id} in their JSON parameters.
 * This validator only checks that those IDs exist as active rows in {@code channels}
 * (for channel and category refs) or {@code roles}. It does not validate other
 * predicate parameter shapes — the engine's runtime evaluators handle parse errors
 * defensively, and we are not in the business of inventing structural validation
 * for shapes the schema doesn't model.
 *
 * <p>Category references are deliberately validated against the {@code channels}
 * table because {@code categories} are tracked separately; we don't currently load
 * categories here, so a category-id validity check would require a {@code CategoryDao}
 * lookup. We perform that lookup as well, see {@link #validate(List)}.
 */
@ApplicationScoped
public class RuleValidator {

    private static final Set<String> CHANNEL_REF_PREDICATES = Set.of(
            "IN_CHANNEL", "NOT_IN_CHANNEL", "IN_VOICE_CHANNEL");
    private static final Set<String> CATEGORY_REF_PREDICATES = Set.of("IN_CATEGORY");
    private static final Set<String> ROLE_REF_PREDICATES = Set.of(
            "MEMBER_HAS_ROLE", "MEMBER_NOT_HAS_ROLE",
            "ROLE_WAS_ADDED", "ROLE_WAS_REMOVED");
        private static final Set<String> KNOWN_RULE_EVENT_TYPES = Set.of(
            "MESSAGE_CREATE",
            "MEMBER_JOIN",
            "MEMBER_LEAVE",
            "MEMBER_ROLE_CHANGE",
            "VOICE_JOIN",
            "VOICE_LEAVE",
            "VOICE_MOVE");

    private final ObjectMapper mapper = new ObjectMapper();

    @Inject ChannelDao channelDao;
    @Inject RoleDao roleDao;
    @Inject edu.franklin.acm.synapse.activity.channel.CategoryDao categoryDao;

    /**
     * Returns the list of reasons a rule's predicates are invalid. Empty list
     * means the rule is structurally valid against current active references.
     */
    public List<String> validate(Rule rule, List<RulePredicate> predicates) {
        List<String> reasons = new ArrayList<>();
        if (rule == null || !KNOWN_RULE_EVENT_TYPES.contains(rule.eventType())) {
            reasons.add("Rule event_type is unknown or unsupported: "
                    + (rule == null ? null : rule.eventType()));
        }
        reasons.addAll(validate(predicates));
        return reasons;
    }

    public List<String> validate(List<RulePredicate> predicates) {
        if (predicates == null || predicates.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> activeChannels = new HashSet<>(channelDao.findAllActiveExtIds());
        Set<Long> activeRoles = new HashSet<>(roleDao.findAllActiveExtIds());
        Set<Long> activeCategories = new HashSet<>(categoryDao.findAllActiveExtIds());
        List<String> reasons = new ArrayList<>();

        for (RulePredicate predicate : predicates) {
            String type = predicate.predicateType();
            if (CHANNEL_REF_PREDICATES.contains(type)) {
                Long extId = extractLong(predicate.parameters(), "channel_ext_id");
                if (extId == null) {
                    reasons.add(type + " is missing 'channel_ext_id' parameter");
                } else if (!activeChannels.contains(extId)) {
                    reasons.add(type + " references unknown or inactive channel " + extId);
                }
            } else if (CATEGORY_REF_PREDICATES.contains(type)) {
                Long extId = extractLong(predicate.parameters(), "category_ext_id");
                if (extId == null) {
                    reasons.add(type + " is missing 'category_ext_id' parameter");
                } else if (!activeCategories.contains(extId)) {
                    reasons.add(type + " references unknown or inactive category " + extId);
                }
            } else if (ROLE_REF_PREDICATES.contains(type)) {
                Long extId = extractLong(predicate.parameters(), "role_ext_id");
                if (extId == null) {
                    reasons.add(type + " is missing 'role_ext_id' parameter");
                } else if (!activeRoles.contains(extId)) {
                    reasons.add(type + " references unknown or inactive role " + extId);
                }
            }
        }
        return reasons;
    }

    private Long extractLong(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(json);
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            if (value.isNumber()) {
                return value.asLong();
            }
            String text = value.asText();
            if (text.isBlank()) return null;
            return Long.valueOf(text);
        } catch (JsonProcessingException | NumberFormatException e) {
            return null;
        }
    }
}
