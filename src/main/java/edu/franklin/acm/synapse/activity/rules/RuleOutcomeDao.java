package edu.franklin.acm.synapse.activity.rules;

import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@RegisterConstructorMapper(RuleOutcome.class)
public interface RuleOutcomeDao {

    @SqlQuery("""
            SELECT id, rule_id, type, p_currency, s_currency, parameters
            FROM rule_outcomes
            WHERE rule_id = :ruleId
            """)
    List<RuleOutcome> findByRuleId(@Bind("ruleId") long ruleId);

    @SqlBatch("""
            INSERT INTO rule_outcomes (rule_id, type, p_currency, s_currency, parameters)
            VALUES (:ruleId, :type, :pCurrency, :sCurrency, :parameters)
            """)
    void insertBatch(@BindMethods List<RuleOutcome> outcomes);

    @SqlUpdate("DELETE FROM rule_outcomes WHERE rule_id = :ruleId")
    void deleteByRuleId(@Bind("ruleId") long ruleId);
}
