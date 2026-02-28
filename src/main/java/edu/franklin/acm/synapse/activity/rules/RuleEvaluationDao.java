package edu.franklin.acm.synapse.activity.rules;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface RuleEvaluationDao {

    @SqlUpdate("""
            INSERT INTO rule_evaluations (rule_id, event_id, member_id)
            VALUES (:ruleId, :eventId, :memberId)
            """)
    @GetGeneratedKeys
    long insert(
            @Bind("ruleId") long ruleId,
            @Bind("eventId") long eventId,
            @Bind("memberId") long memberId);

    @SqlQuery("""
            SELECT COUNT(*) FROM rule_evaluations
            WHERE rule_id = :ruleId AND event_id = :eventId
            """)
    int countByRuleAndEvent(
            @Bind("ruleId") long ruleId,
            @Bind("eventId") long eventId);

    @SqlQuery("""
            SELECT COUNT(*) FROM rule_evaluations
            WHERE rule_id = :ruleId
              AND member_id = :memberId
              AND created_at > :since
            """)
    int countRecentByRuleAndMember(
            @Bind("ruleId") long ruleId,
            @Bind("memberId") long memberId,
            @Bind("since") String since);
}
