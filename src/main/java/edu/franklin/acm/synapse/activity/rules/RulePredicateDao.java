package edu.franklin.acm.synapse.activity.rules;

import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@RegisterConstructorMapper(RulePredicate.class)
public interface RulePredicateDao {

    @SqlQuery("""
            SELECT id, rule_id, predicate_type, parameters, sort_order
            FROM rule_predicates
            WHERE rule_id = :ruleId
            ORDER BY sort_order
            """)
    List<RulePredicate> findByRuleId(@Bind("ruleId") long ruleId);

    @SqlBatch("""
            INSERT INTO rule_predicates (rule_id, predicate_type, parameters, sort_order)
            VALUES (:ruleId, :predicateType, :parameters, :sortOrder)
            """)
    void insertBatch(@BindMethods List<RulePredicate> predicates);

    @SqlUpdate("DELETE FROM rule_predicates WHERE rule_id = :ruleId")
    void deleteByRuleId(@Bind("ruleId") long ruleId);
}
