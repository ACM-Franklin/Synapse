-- Operational hardening for the first frontend-facing API slice.

CREATE TABLE IF NOT EXISTS reward_replay_jobs (
    id                  INTEGER PRIMARY KEY,
    status              VARCHAR NOT NULL,
    batch_size          INTEGER NOT NULL DEFAULT 200,
    batches_processed   INTEGER NOT NULL DEFAULT 0,
    scanned_count       INTEGER NOT NULL DEFAULT 0,
    replayed_count      INTEGER NOT NULL DEFAULT 0,
    failed_count        INTEGER NOT NULL DEFAULT 0,
    last_message_id     BIGINT NOT NULL DEFAULT 0,
    started_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP,
    error_message       TEXT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS reward_replay_jobs_status_idx
    ON reward_replay_jobs (status, started_at);

CREATE UNIQUE INDEX IF NOT EXISTS reward_replay_jobs_single_running_uq
    ON reward_replay_jobs (status)
    WHERE status = 'RUNNING';

CREATE INDEX IF NOT EXISTS members_p_currency_leaderboard_idx
    ON members (p_currency DESC, id ASC);

CREATE INDEX IF NOT EXISTS members_s_currency_leaderboard_idx
    ON members (s_currency DESC, id ASC);