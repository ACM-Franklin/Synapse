package edu.franklin.acm.synapse.api.service;

import java.util.ArrayList;
import java.util.List;

import edu.franklin.acm.synapse.activity.rules.Rule;
import edu.franklin.acm.synapse.activity.rules.RuleDao;
import edu.franklin.acm.synapse.activity.rules.RuleOutcome;
import edu.franklin.acm.synapse.activity.rules.RuleOutcomeDao;
import edu.franklin.acm.synapse.activity.rules.RulePredicate;
import edu.franklin.acm.synapse.activity.rules.RulePredicateDao;
import edu.franklin.acm.synapse.api.dto.RuleDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Assembles rule DTOs from DAO rows. Lives in {@code api/service} so the
 * frontend-facing shape is not entangled with the raw {@code RuleDao}.
 */
@ApplicationScoped
public class RuleQueryService {

    @Inject RuleDao ruleDao;
    @Inject RulePredicateDao predicateDao;
    @Inject RuleOutcomeDao outcomeDao;
    @Inject RuleValidator validator;

    public List<RuleDto> findAll() {
        List<RuleDto> out = new ArrayList<>();
        for (Rule rule : ruleDao.findAll()) {
            out.add(toDto(rule));
        }
        return out;
    }

    public RuleDto findById(long ruleId) {
        for (Rule rule : ruleDao.findAll()) {
            if (rule.id() == ruleId) {
                return toDto(rule);
            }
        }
        return null;
    }

    public int countInvalid() {
        int count = 0;
        for (Rule rule : ruleDao.findAll()) {
            List<RulePredicate> predicates = predicateDao.findByRuleId(rule.id());
            if (!validator.validate(rule, predicates).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private RuleDto toDto(Rule rule) {
        List<RulePredicate> predicates = predicateDao.findByRuleId(rule.id());
        List<RuleOutcome> outcomes = outcomeDao.findByRuleId(rule.id());
        List<String> invalidReasons = validator.validate(rule, predicates);

        List<RuleDto.PredicateDto> predicateDtos = new ArrayList<>(predicates.size());
        for (RulePredicate predicate : predicates) {
            predicateDtos.add(new RuleDto.PredicateDto(
                    predicate.id(),
                    predicate.predicateType(),
                    predicate.parameters(),
                    predicate.sortOrder()));
        }
        List<RuleDto.OutcomeDto> outcomeDtos = new ArrayList<>(outcomes.size());
        for (RuleOutcome outcome : outcomes) {
            outcomeDtos.add(new RuleDto.OutcomeDto(
                    outcome.id(),
                    outcome.type(),
                    outcome.pCurrency(),
                    outcome.sCurrency(),
                    outcome.parameters()));
        }

        return new RuleDto(
                rule.id(),
                rule.name(),
                rule.description(),
                rule.eventType(),
                rule.enabled(),
                rule.appliesLive(),
                rule.appliesHistoric(),
                rule.cooldownSeconds(),
                invalidReasons.isEmpty(),
                invalidReasons,
                predicateDtos,
                outcomeDtos,
                rule.createdAt(),
                rule.updatedAt());
    }
}
