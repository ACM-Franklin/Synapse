package edu.franklin.acm.synapse.activity.rules;

import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@RegisterConstructorMapper(RewardLedgerEntry.class)
public interface RewardLedgerDao {

    @SqlUpdate("""
            INSERT INTO reward_ledger (
                rule_evaluation_id,
                rule_outcome_id,
                rule_id,
                event_id,
                member_id,
                currency_type,
                amount,
                transaction_type,
                reverses_reward_ledger_id
            ) VALUES (
                :ruleEvaluationId,
                :ruleOutcomeId,
                :ruleId,
                :eventId,
                :memberId,
                :currencyType,
                :amount,
                :transactionType,
                :reversesRewardLedgerId
            )
            """)
    @GetGeneratedKeys
    long insert(@BindMethods RewardLedgerEntry entry);

    @SqlQuery("""
            SELECT id, rule_evaluation_id, rule_outcome_id, rule_id, event_id,
                   member_id, currency_type, amount, transaction_type,
                   reverses_reward_ledger_id, created_at
            FROM reward_ledger
            WHERE rule_evaluation_id = :ruleEvaluationId
            ORDER BY id
            """)
    List<RewardLedgerEntry> findByRuleEvaluationId(@Bind("ruleEvaluationId") long ruleEvaluationId);

    @SqlQuery("""
            SELECT id, rule_evaluation_id, rule_outcome_id, rule_id, event_id,
                   member_id, currency_type, amount, transaction_type,
                   reverses_reward_ledger_id, created_at
            FROM reward_ledger
            WHERE member_id = :memberId
            ORDER BY id DESC
            LIMIT :limit
            """)
    List<RewardLedgerEntry> findRecentByMember(@Bind("memberId") long memberId, @Bind("limit") int limit);

    @SqlQuery("SELECT award.id, award.rule_evaluation_id, award.rule_outcome_id, "
            + "award.rule_id, award.event_id, award.member_id, "
            + "award.currency_type, award.amount, award.transaction_type, "
            + "award.reverses_reward_ledger_id, award.created_at "
            + "FROM reward_ledger award "
            + "WHERE award.event_id = :eventId "
            + "AND award.transaction_type = 'AWARD' "
            + "AND NOT EXISTS ("
            + "SELECT 1 FROM reward_ledger reversal "
            + "WHERE reversal.reverses_reward_ledger_id = award.id"
            + ") "
            + "ORDER BY award.id")
    List<RewardLedgerEntry> findUnreversedAwardsByEventId(@Bind("eventId") long eventId);
}
