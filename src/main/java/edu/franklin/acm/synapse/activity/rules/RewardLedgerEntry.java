package edu.franklin.acm.synapse.activity.rules;

public record RewardLedgerEntry(
        long id,
        long ruleEvaluationId,
        long ruleOutcomeId,
        long ruleId,
        long eventId,
        long memberId,
        String currencyType,
        int amount,
        String transactionType,
        Long reversesRewardLedgerId,
        String subjectType,
        Long subjectExtId,
        String createdAt) {
}
