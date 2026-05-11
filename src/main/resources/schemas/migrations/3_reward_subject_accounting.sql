-- Add stable reward subject identity so edits, deletes, and replay can reason
-- about a message as one subject instead of as unrelated event IDs.

CREATE TABLE IF NOT EXISTS reward_ledger (
    id                         INTEGER PRIMARY KEY,
    rule_evaluation_id         BIGINT NOT NULL,
    rule_outcome_id            BIGINT NOT NULL,
    rule_id                    BIGINT NOT NULL,
    event_id                   BIGINT NOT NULL,
    member_id                  BIGINT NOT NULL,
    currency_type              VARCHAR NOT NULL,
    amount                     INTEGER NOT NULL,
    transaction_type           VARCHAR NOT NULL DEFAULT 'AWARD',
    reverses_reward_ledger_id  BIGINT,
    created_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rule_evaluation_id)        REFERENCES rule_evaluations (id),
    FOREIGN KEY (rule_outcome_id)           REFERENCES rule_outcomes (id),
    FOREIGN KEY (rule_id)                   REFERENCES rules (id),
    FOREIGN KEY (event_id)                  REFERENCES events (id),
    FOREIGN KEY (member_id)                 REFERENCES members (id),
    FOREIGN KEY (reverses_reward_ledger_id) REFERENCES reward_ledger (id)
);

CREATE INDEX IF NOT EXISTS reward_ledger_member_idx ON reward_ledger (member_id, created_at);
CREATE INDEX IF NOT EXISTS reward_ledger_event_idx ON reward_ledger (event_id);
CREATE INDEX IF NOT EXISTS reward_ledger_rule_eval_idx ON reward_ledger (rule_evaluation_id);
CREATE UNIQUE INDEX IF NOT EXISTS reward_ledger_award_uq
    ON reward_ledger (rule_evaluation_id, rule_outcome_id, currency_type, transaction_type)
    WHERE transaction_type = 'AWARD';

ALTER TABLE reward_ledger ADD COLUMN subject_type VARCHAR;
ALTER TABLE reward_ledger ADD COLUMN subject_ext_id BIGINT;

CREATE INDEX IF NOT EXISTS reward_ledger_subject_idx
    ON reward_ledger (subject_type, subject_ext_id, created_at);

UPDATE reward_ledger
SET subject_type = 'MESSAGE',
    subject_ext_id = (
        SELECT m.ext_id
        FROM messages m
        WHERE m.event_id = reward_ledger.event_id
    )
WHERE subject_type IS NULL
  AND EXISTS (
      SELECT 1
      FROM messages m
      WHERE m.event_id = reward_ledger.event_id
  );

UPDATE reward_ledger
SET subject_type = (
        SELECT award.subject_type
        FROM reward_ledger award
        WHERE award.id = reward_ledger.reverses_reward_ledger_id
    ),
    subject_ext_id = (
        SELECT award.subject_ext_id
        FROM reward_ledger award
        WHERE award.id = reward_ledger.reverses_reward_ledger_id
    )
WHERE subject_type IS NULL
  AND reverses_reward_ledger_id IS NOT NULL;