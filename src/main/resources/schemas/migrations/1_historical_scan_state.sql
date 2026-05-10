-- Add durable historical scan job and checkpoint state for existing databases.

CREATE TABLE IF NOT EXISTS historical_scan_jobs (
    id               INTEGER PRIMARY KEY,
    guild_ext_id     BIGINT NOT NULL,
    status           VARCHAR NOT NULL,
    started_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at     TIMESTAMP,
    checkpoint_count INTEGER NOT NULL DEFAULT 0,
    error_message    TEXT,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS historical_scan_jobs_guild_ext_id_idx
    ON historical_scan_jobs (guild_ext_id);

CREATE TABLE IF NOT EXISTS historical_scan_checkpoints (
    id                  INTEGER PRIMARY KEY,
    guild_ext_id        BIGINT NOT NULL,
    channel_ext_id      BIGINT NOT NULL,
    last_message_ext_id BIGINT NOT NULL,
    scanned_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (guild_ext_id, channel_ext_id)
);

CREATE INDEX IF NOT EXISTS historical_scan_checkpoints_guild_ext_id_idx
    ON historical_scan_checkpoints (guild_ext_id);