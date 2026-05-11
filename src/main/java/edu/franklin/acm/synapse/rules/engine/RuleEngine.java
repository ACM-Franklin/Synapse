package edu.franklin.acm.synapse.rules.engine;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerDao;
import edu.franklin.acm.synapse.activity.rules.RewardLedgerEntry;
import edu.franklin.acm.synapse.activity.rules.Rule;
import edu.franklin.acm.synapse.activity.rules.RuleDao;
import edu.franklin.acm.synapse.activity.rules.RuleEvaluationDao;
import edu.franklin.acm.synapse.activity.rules.RuleOutcome;
import edu.franklin.acm.synapse.activity.rules.RuleOutcomeDao;
import edu.franklin.acm.synapse.activity.rules.RulePredicate;
import edu.franklin.acm.synapse.activity.rules.RulePredicateDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

/**
 * Core rule evaluation engine. Observes async CDI events fired by the live
 * scanner after event persistence. Evaluates matching rules against the
 * event context and dispatches outcomes.
 */
@ApplicationScoped
public class RuleEngine {

    private static final Logger log = Logger.getLogger(RuleEngine.class);
    private static final DateTimeFormatter SQL_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    RuleDao ruleDao;

    @Inject
    RulePredicateDao rulePredicateDao;

    @Inject
    RuleEvaluationDao ruleEvaluationDao;

    @Inject
    Jdbi jdbi;

    @Inject
    jakarta.enterprise.inject.Instance<PredicateEvaluator> evaluatorBeans;

    /**
     * Async observer — receives evaluation requests from the live scanner.
     */
    public void onEvaluationRequest(@ObservesAsync RuleEvaluationRequest request) {
        RuleContext ctx = request.context();
        try {
            if (request.replaceSubjectAwards()) {
                evaluateReplacingSubject(ctx, false);
            } else {
                evaluate(ctx);
            }
        } catch (Exception e) {
            log.errorf(e, "Rule evaluation failed for event %d (type=%s, member=%d)",
                    ctx.eventId(), ctx.eventType(), ctx.memberId());
        }
    }

    /**
     * Evaluate all enabled rules matching the event type against the context.
     */
    void evaluate(RuleContext ctx) {
        List<Rule> candidates = ruleDao.findEnabledByEventType(ctx.eventType());
        if (candidates.isEmpty()) return;

        for (Rule rule : candidates) {
            if (!rule.appliesLive()) continue;

            try {
                evaluateRule(rule, ctx);
            } catch (Exception e) {
                log.errorf(e, "Error evaluating rule '%s' for event %d", rule.name(), ctx.eventId());
            }
        }
    }

    void evaluateReplacingSubject(RuleContext ctx, boolean ignoreCooldown) {
        if (ctx.subjectType() == null || ctx.subjectExtId() == null) {
            evaluate(ctx);
            return;
        }

        List<Rule> candidates = ruleDao.findEnabledByEventType(ctx.eventType());
        if (candidates.isEmpty()) return;

        jdbi.useTransaction(handle -> {
            RuleEvaluationDao txEvaluationDao = attachDao(handle, RuleEvaluationDao.class);
            RuleOutcomeDao txOutcomeDao = attachDao(handle, RuleOutcomeDao.class);
            RewardLedgerDao txRewardLedgerDao = attachDao(handle, RewardLedgerDao.class);
            MemberDao txMemberDao = attachDao(handle, MemberDao.class);
            RulePredicateDao txPredicateDao = attachDao(handle, RulePredicateDao.class);

            List<RewardLedgerEntry> activeAwards = txRewardLedgerDao
                .findUnreversedAwardsBySubject(ctx.subjectType(), ctx.subjectExtId());
            RuleContext evaluationCtx = normalizeReplacementContext(ctx, activeAwards);
            Map<Long, List<RewardLedgerEntry>> activeAwardsByRule = activeAwards
                    .stream()
                    .collect(Collectors.groupingBy(RewardLedgerEntry::ruleId));

            for (Rule rule : candidates) {
                if (!rule.appliesLive()) continue;

                try {
                    evaluateReplacementRule(
                            rule,
                            evaluationCtx,
                            activeAwardsByRule.getOrDefault(rule.id(), Collections.emptyList()),
                            ignoreCooldown,
                            txPredicateDao,
                            txEvaluationDao,
                            txOutcomeDao,
                            txRewardLedgerDao,
                            txMemberDao);
                } catch (Exception e) {
                    log.errorf(e, "Error evaluating replacement for rule '%s' on event %d", rule.name(), ctx.eventId());
                }
            }
        });
    }

    private void evaluateRule(Rule rule, RuleContext ctx) {
        // Deduplication check
        if (ruleEvaluationDao.countByRuleAndEvent(rule.id(), ctx.eventId()) > 0) {
            return;
        }

        // Cooldown check
        if (rule.cooldownSeconds() > 0) {
            String since = LocalDateTime.now(ZoneOffset.UTC)
                    .minusSeconds(rule.cooldownSeconds())
                    .format(SQL_TIMESTAMP_FORMAT);
            if (ruleEvaluationDao.countRecentByRuleAndMember(rule.id(), ctx.memberId(), since) > 0) {
                return;
            }
        }

        // Load and evaluate predicates
        List<RulePredicate> predicates = rulePredicateDao.findByRuleId(rule.id());
        for (RulePredicate predicate : predicates) {
            if (!evaluatePredicate(predicate, ctx)) {
                return; // Short-circuit: predicate failed, rule does not fire
            }
        }

        // All predicates passed — fire the rule
        fire(rule, ctx);
    }

    private void evaluateReplacementRule(Rule rule, RuleContext ctx,
                                         List<RewardLedgerEntry> existingAwards,
                                         boolean ignoreCooldown,
                                         RulePredicateDao predicateDao,
                                         RuleEvaluationDao evaluationDao,
                                         RuleOutcomeDao outcomeDao,
                                         RewardLedgerDao rewardLedgerDao,
                                         MemberDao memberDao) {
        if (evaluationDao.countByRuleAndEvent(rule.id(), ctx.eventId()) > 0) {
            return;
        }

        if (!ignoreCooldown && existingAwards.isEmpty() && rule.cooldownSeconds() > 0) {
            String since = LocalDateTime.now(ZoneOffset.UTC)
                    .minusSeconds(rule.cooldownSeconds())
                    .format(SQL_TIMESTAMP_FORMAT);
            if (evaluationDao.countRecentByRuleAndMember(rule.id(), ctx.memberId(), since) > 0) {
                return;
            }
        }

        List<RulePredicate> predicates = predicateDao.findByRuleId(rule.id());
        for (RulePredicate predicate : predicates) {
            if (!evaluatePredicate(predicate, ctx)) {
                applyReplacementDiff(
                        existingAwards,
                        List.of(),
                        ctx,
                        null,
                        0L,
                        rewardLedgerDao,
                        memberDao);
                return;
            }
        }

        List<RuleOutcome> outcomes = outcomeDao.findByRuleId(rule.id());
        List<DesiredRewardLedgerEntry> desiredAwards = desiredAwards(outcomes, ctx);
        if (existingAwardsEqualDesired(existingAwards, desiredAwards)) {
            evaluationDao.insert(rule.id(), ctx.eventId(), ctx.memberId());
            return;
        }

        long ruleEvaluationId = evaluationDao.insert(rule.id(), ctx.eventId(), ctx.memberId());
        applyReplacementDiff(existingAwards, desiredAwards, ctx, rule, ruleEvaluationId, rewardLedgerDao, memberDao);
    }

    private boolean evaluatePredicate(RulePredicate predicate, RuleContext ctx) {
        for (PredicateEvaluator evaluator : evaluatorBeans) {
            if (evaluator.handles(predicate.predicateType())) {
                return evaluator.evaluate(predicate.predicateType(), ctx, predicate.parameters());
            }
        }
        log.warnf("No evaluator found for predicate type '%s' on rule_predicate %d",
                predicate.predicateType(), predicate.id());
        return false; // Unknown predicate type = fail safe
    }

    private void fire(Rule rule, RuleContext ctx) {
        log.infof("Rule '%s' fired for event %d (member %d)", rule.name(), ctx.eventId(), ctx.memberId());

        jdbi.useTransaction(handle -> {
            RuleEvaluationDao txEvaluationDao = attachDao(handle, RuleEvaluationDao.class);
            RuleOutcomeDao txOutcomeDao = attachDao(handle, RuleOutcomeDao.class);
            RewardLedgerDao txRewardLedgerDao = attachDao(handle, RewardLedgerDao.class);
            MemberDao txMemberDao = attachDao(handle, MemberDao.class);

            long ruleEvaluationId = txEvaluationDao.insert(rule.id(), ctx.eventId(), ctx.memberId());
            List<RuleOutcome> outcomes = txOutcomeDao.findByRuleId(rule.id());
            for (RuleOutcome outcome : outcomes) {
                dispatchOutcome(outcome, ctx, ruleEvaluationId, txRewardLedgerDao, txMemberDao);
            }
        });
    }

    private void dispatchOutcome(RuleOutcome outcome, RuleContext ctx, long ruleEvaluationId,
                                 RewardLedgerDao rewardLedgerDao, MemberDao memberDao) {
        switch (outcome.type()) {
            case "CURRENCY" -> dispatchCurrency(outcome, ctx, ruleEvaluationId, rewardLedgerDao, memberDao);
            case "ACHIEVEMENT" -> log.infof("Achievement outcome for member %d (stub — no achievements table yet)",
                    ctx.memberId());
            case "ANNOUNCEMENT" -> log.infof("Announcement outcome for member %d (stub — no delivery mechanism yet)",
                    ctx.memberId());
            default -> log.warnf("Unknown outcome type '%s' on rule_outcome %d", outcome.type(), outcome.id());
        }
    }

    private void dispatchCurrency(RuleOutcome outcome, RuleContext ctx, long ruleEvaluationId,
                                  RewardLedgerDao rewardLedgerDao, MemberDao memberDao) {
        if (outcome.pCurrency() != null && outcome.pCurrency() != 0) {
            recordCurrencyAward(ruleEvaluationId, outcome, ctx, rewardLedgerDao, "PRIMARY", outcome.pCurrency());
            memberDao.incrementPCurrency(ctx.memberId(), outcome.pCurrency());
            log.debugf("Granted %d primary currency to member %d",
                    (Object) outcome.pCurrency(), (Object) ctx.memberId());
        }
        if (outcome.sCurrency() != null && outcome.sCurrency() != 0) {
            recordCurrencyAward(ruleEvaluationId, outcome, ctx, rewardLedgerDao, "SECONDARY", outcome.sCurrency());
            memberDao.incrementSCurrency(ctx.memberId(), outcome.sCurrency());
            log.debugf("Granted %d secondary currency to member %d",
                    (Object) outcome.sCurrency(), (Object) ctx.memberId());
        }
    }

    private void recordCurrencyAward(long ruleEvaluationId, RuleOutcome outcome, RuleContext ctx,
                                     RewardLedgerDao rewardLedgerDao, String currencyType, int amount) {
        rewardLedgerDao.insert(toRewardLedgerEntry(desiredAward(ruleEvaluationId, outcome, ctx, currencyType, amount)));
    }

    private void applyReplacementDiff(List<RewardLedgerEntry> existingAwards,
                                      List<DesiredRewardLedgerEntry> desiredAwards,
                                      RuleContext ctx,
                                      Rule rule,
                                      long ruleEvaluationId,
                                      RewardLedgerDao rewardLedgerDao,
                                      MemberDao memberDao) {
        List<RewardLedgerEntry> unmatchedExisting = new ArrayList<>(existingAwards);
        List<DesiredRewardLedgerEntry> unmatchedDesired = new ArrayList<>(desiredAwards);

        for (DesiredRewardLedgerEntry desired : desiredAwards) {
            RewardLedgerEntry matched = findMatchingExisting(unmatchedExisting, desired);
            if (matched != null) {
                unmatchedExisting.remove(matched);
                unmatchedDesired.remove(desired);
            }
        }

        reverseAwards(unmatchedExisting, ctx.eventId(), rewardLedgerDao, memberDao);
        if (rule != null) {
            log.infof("Rule '%s' fired for event %d (member %d)", rule.name(), ctx.eventId(), ctx.memberId());
        }
        for (DesiredRewardLedgerEntry desired : unmatchedDesired) {
            rewardLedgerDao.insert(toRewardLedgerEntry(desired.withRuleEvaluationId(ruleEvaluationId)));
            applyMemberBalance(memberDao, desired.currencyType(), desired.memberId(), desired.amount());
        }
    }

    private List<DesiredRewardLedgerEntry> desiredAwards(List<RuleOutcome> outcomes, RuleContext ctx) {
        List<DesiredRewardLedgerEntry> desired = new ArrayList<>();
        for (RuleOutcome outcome : outcomes) {
            if (!"CURRENCY".equals(outcome.type())) {
                continue;
            }
            if (outcome.pCurrency() != null && outcome.pCurrency() != 0) {
                desired.add(desiredAward(0L, outcome, ctx, "PRIMARY", outcome.pCurrency()));
            }
            if (outcome.sCurrency() != null && outcome.sCurrency() != 0) {
                desired.add(desiredAward(0L, outcome, ctx, "SECONDARY", outcome.sCurrency()));
            }
        }
        return desired;
    }

    private DesiredRewardLedgerEntry desiredAward(long ruleEvaluationId, RuleOutcome outcome, RuleContext ctx,
                                                  String currencyType, int amount) {
        return new DesiredRewardLedgerEntry(
                ruleEvaluationId,
                outcome.id(),
                outcome.ruleId(),
                ctx.eventId(),
                ctx.memberId(),
                currencyType,
                amount,
                ctx.subjectType(),
                ctx.subjectExtId());
    }

    private RewardLedgerEntry toRewardLedgerEntry(DesiredRewardLedgerEntry desired) {
        return new RewardLedgerEntry(
                0L,
                desired.ruleEvaluationId(),
                desired.ruleOutcomeId(),
                desired.ruleId(),
                desired.eventId(),
                desired.memberId(),
                desired.currencyType(),
                desired.amount(),
                "AWARD",
                null,
                desired.subjectType(),
                desired.subjectExtId(),
                null);
    }

    private boolean existingAwardsEqualDesired(List<RewardLedgerEntry> existingAwards,
                                               List<DesiredRewardLedgerEntry> desiredAwards) {
        if (existingAwards.size() != desiredAwards.size()) {
            return false;
        }

        List<RewardLedgerEntry> unmatchedExisting = new ArrayList<>(existingAwards);
        for (DesiredRewardLedgerEntry desired : desiredAwards) {
            RewardLedgerEntry matched = findMatchingExisting(unmatchedExisting, desired);
            if (matched == null) {
                return false;
            }
            unmatchedExisting.remove(matched);
        }
        return unmatchedExisting.isEmpty();
    }

    private RewardLedgerEntry findMatchingExisting(List<RewardLedgerEntry> existingAwards,
                                                   DesiredRewardLedgerEntry desired) {
        for (RewardLedgerEntry existing : existingAwards) {
            if (existing.ruleOutcomeId() == desired.ruleOutcomeId()
                    && existing.ruleId() == desired.ruleId()
                    && existing.memberId() == desired.memberId()
                    && existing.amount() == desired.amount()
                    && Objects.equals(existing.currencyType(), desired.currencyType())
                    && Objects.equals(existing.subjectType(), desired.subjectType())
                    && Objects.equals(existing.subjectExtId(), desired.subjectExtId())) {
                return existing;
            }
        }
        return null;
    }

    private void reverseAwards(List<RewardLedgerEntry> awards, long reversalEventId,
                               RewardLedgerDao rewardLedgerDao, MemberDao memberDao) {
        for (RewardLedgerEntry award : awards) {
            rewardLedgerDao.insert(new RewardLedgerEntry(
                    0L,
                    award.ruleEvaluationId(),
                    award.ruleOutcomeId(),
                    award.ruleId(),
                    reversalEventId,
                    award.memberId(),
                    award.currencyType(),
                    -award.amount(),
                    "REVERSAL",
                    award.id(),
                    award.subjectType(),
                    award.subjectExtId(),
                    null));
            reverseMemberBalance(memberDao, award);
        }
    }

    private void reverseMemberBalance(MemberDao memberDao, RewardLedgerEntry award) {
        switch (award.currencyType()) {
            case "PRIMARY" -> memberDao.incrementPCurrency(award.memberId(), -award.amount());
            case "SECONDARY" -> memberDao.incrementSCurrency(award.memberId(), -award.amount());
            default -> log.warnf("Cannot reverse unknown currency type '%s' on reward ledger %d",
                    award.currencyType(), award.id());
        }
    }

    private void applyMemberBalance(MemberDao memberDao, String currencyType, long memberId, int amount) {
        switch (currencyType) {
            case "PRIMARY" -> memberDao.incrementPCurrency(memberId, amount);
            case "SECONDARY" -> memberDao.incrementSCurrency(memberId, amount);
            default -> log.warnf("Cannot apply unknown currency type '%s' for member %d", currencyType, memberId);
        }
    }

    private RuleContext normalizeReplacementContext(RuleContext ctx, List<RewardLedgerEntry> activeAwards) {
        if (ctx.memberPCurrency() == null || ctx.memberSCurrency() == null || activeAwards.isEmpty()) {
            return ctx;
        }

        int primaryAdjustment = 0;
        int secondaryAdjustment = 0;
        for (RewardLedgerEntry award : activeAwards) {
            switch (award.currencyType()) {
                case "PRIMARY" -> primaryAdjustment += award.amount();
                case "SECONDARY" -> secondaryAdjustment += award.amount();
                default -> {
                }
            }
        }

        return ctx.withMemberCurrencies(
                ctx.memberPCurrency() - primaryAdjustment,
                ctx.memberSCurrency() - secondaryAdjustment);
    }

    private record DesiredRewardLedgerEntry(
            long ruleEvaluationId,
            long ruleOutcomeId,
            long ruleId,
            long eventId,
            long memberId,
            String currencyType,
            int amount,
            String subjectType,
            Long subjectExtId) {

        private DesiredRewardLedgerEntry withRuleEvaluationId(long newRuleEvaluationId) {
            return new DesiredRewardLedgerEntry(
                    newRuleEvaluationId,
                    ruleOutcomeId,
                    ruleId,
                    eventId,
                    memberId,
                    currencyType,
                    amount,
                    subjectType,
                    subjectExtId);
        }
    }

    @SuppressWarnings("null")
    private static <T> T attachDao(Handle handle, Class<T> daoType) {
        return handle.attach(daoType);
    }
}
