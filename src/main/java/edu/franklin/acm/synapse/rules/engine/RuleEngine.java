package edu.franklin.acm.synapse.rules.engine;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jboss.logging.Logger;
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
            evaluate(ctx);
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

    private void evaluateRule(Rule rule, RuleContext ctx) {
        // Deduplication check
        if (ruleEvaluationDao.countByRuleAndEvent(rule.id(), ctx.eventId()) > 0) {
            return;
        }

        // Cooldown check
        if (rule.cooldownSeconds() > 0) {
            String since = LocalDateTime.now(ZoneOffset.UTC)
                    .minusSeconds(rule.cooldownSeconds())
                    .toString();
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
            RuleEvaluationDao txEvaluationDao = handle.attach(RuleEvaluationDao.class);
            RuleOutcomeDao txOutcomeDao = handle.attach(RuleOutcomeDao.class);
            RewardLedgerDao txRewardLedgerDao = handle.attach(RewardLedgerDao.class);
            MemberDao txMemberDao = handle.attach(MemberDao.class);

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
        rewardLedgerDao.insert(new RewardLedgerEntry(
                0L,
                ruleEvaluationId,
                outcome.id(),
                outcome.ruleId(),
                ctx.eventId(),
                ctx.memberId(),
                currencyType,
                amount,
                "AWARD",
                null,
                null));
    }
}
