package edu.franklin.acm.synapse.rules.engine.predicates;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.member.MemberRoleDao;
import edu.franklin.acm.synapse.rules.engine.PredicateEvaluator;
import edu.franklin.acm.synapse.rules.engine.RuleContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Handles predicates requiring database lookups against member state:
 * role membership, server age, account age, first join detection,
 * and role change event matching.
 */
@ApplicationScoped
public class MemberLookupEvaluator implements PredicateEvaluator {

    private static final Logger log = Logger.getLogger(MemberLookupEvaluator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> HANDLED = Set.of(
            "MEMBER_HAS_ROLE", "MEMBER_NOT_HAS_ROLE",
            "MIN_SERVER_AGE_DAYS", "MIN_ACCOUNT_AGE_DAYS",
            "MEMBER_IS_FIRST_JOIN", "MEMBER_IS_REJOIN",
            "ROLE_WAS_ADDED", "ROLE_WAS_REMOVED"
    );

    @Inject
    MemberRoleDao memberRoleDao;

    @Inject
    MemberDao memberDao;

    @Inject
    EventDao eventDao;

    @Override
    public boolean handles(String predicateType) {
        return HANDLED.contains(predicateType);
    }

    @Override
    public boolean evaluate(String predicateType, RuleContext ctx, String parametersJson) {
        try {
            return switch (predicateType) {
                case "MEMBER_HAS_ROLE" -> hasRole(ctx, parametersJson, false);
                case "MEMBER_NOT_HAS_ROLE" -> hasRole(ctx, parametersJson, true);
                case "MIN_SERVER_AGE_DAYS" -> minServerAgeDays(ctx, parametersJson);
                case "MIN_ACCOUNT_AGE_DAYS" -> minAccountAgeDays(ctx, parametersJson);
                case "MEMBER_IS_FIRST_JOIN" -> isFirstJoin(ctx);
                case "MEMBER_IS_REJOIN" -> !isFirstJoin(ctx);
                case "ROLE_WAS_ADDED" -> roleWasChanged(ctx, parametersJson, true);
                case "ROLE_WAS_REMOVED" -> roleWasChanged(ctx, parametersJson, false);
                default -> false;
            };
        } catch (Exception e) {
            log.warnf(e, "MemberLookupEvaluator failed for predicate '%s' (member %d)", predicateType, ctx.memberId());
            return false;
        }
    }

    private boolean hasRole(RuleContext ctx, String parametersJson, boolean negate) throws Exception {
        if (parametersJson == null) return false;
        JsonNode params = MAPPER.readTree(parametersJson);
        long roleExtId = params.path("role_ext_id").asLong();
        List<Long> roles = memberRoleDao.findRolesByMemberId(ctx.memberId());
        boolean has = roles.contains(roleExtId);
        return negate != has;
    }

    private boolean minServerAgeDays(RuleContext ctx, String parametersJson) throws Exception {
        if (parametersJson == null) return false;
        String joinedAt = ctx.memberJoinedAt();
        if (joinedAt == null) {
            joinedAt = memberDao.findJoinedAt(ctx.memberId());
        }
        if (joinedAt == null) return false;
        JsonNode params = MAPPER.readTree(parametersJson);
        int threshold = params.path("threshold").asInt();
        LocalDateTime joined = LocalDateTime.parse(joinedAt);
        long days = ChronoUnit.DAYS.between(joined, LocalDateTime.now(ZoneOffset.UTC));
        return days >= threshold;
    }

    private boolean minAccountAgeDays(RuleContext ctx, String parametersJson) throws Exception {
        if (parametersJson == null) return false;
        Long memberExtId = ctx.memberExtId();
        if (memberExtId == null) {
            memberExtId = memberDao.findExtIdById(ctx.memberId());
        }
        if (memberExtId == null) return false;
        // Discord snowflake epoch: 2015-01-01T00:00:00Z
        long discordEpochMs = 1420070400000L;
        long timestampMs = (memberExtId >> 22) + discordEpochMs;
        LocalDateTime accountCreated = LocalDateTime.ofEpochSecond(
                timestampMs / 1000, 0, ZoneOffset.UTC);
        JsonNode params = MAPPER.readTree(parametersJson);
        int threshold = params.path("threshold").asInt();
        long days = ChronoUnit.DAYS.between(accountCreated, LocalDateTime.now(ZoneOffset.UTC));
        return days >= threshold;
    }

    private boolean isFirstJoin(RuleContext ctx) {
        // Count all MEMBER_JOIN events for this member. At the point of evaluation
        // the current event is already persisted, so count == 1 means first join.
        int joinCount = eventDao.countByMemberAndType(ctx.memberId(), "MEMBER_JOIN");
        return joinCount <= 1;
    }

    private boolean roleWasChanged(RuleContext ctx, String parametersJson, boolean added) throws Exception {
        if (parametersJson == null) return false;
        JsonNode params = MAPPER.readTree(parametersJson);
        String roleExtId = params.path("role_ext_id").asText();
        String changeList = added ? ctx.rolesAdded() : ctx.rolesRemoved();
        if (changeList == null || changeList.isEmpty()) return false;
        for (String id : changeList.split(",")) {
            if (id.trim().equals(roleExtId)) return true;
        }
        return false;
    }
}
