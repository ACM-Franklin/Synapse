-- Synapse Schema — Single-Guild Model
--
-- One instance = one guild. All data is implicitly scoped to this guild.
-- Design principles:
--   No JSON. All queryable data lives in real SQL columns.
--   Events are a lean parent table. Child tables hold type-specific data.
--   Message edits UPSERT — only the current state is kept, no edit history.
--   Rewards are derived by the rule engine, never stored on events.
-- Migration is not required when booting fresh. The schema is always
-- re-applied idempotently. Nuclear overwrite of any existing database
-- file is safe during development.
--
-- Timestamp convention for Discord-origin tables:
--   created_at  = when the entity was created on Discord (explicit, from JDA)
--   ingested_at = when the row was inserted into this database (DB default)
--   updated_at  = when the row was last modified in this database (DB default)

-- Tracks schema migrations applied to this database.
CREATE TABLE IF NOT EXISTS migrations (
    id          INTEGER NOT NULL PRIMARY KEY,
    name        VARCHAR NOT NULL,
    succeeded   BOOLEAN NOT NULL,
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Metadata about the guild this instance manages.
CREATE TABLE IF NOT EXISTS guild_metadata (
    id          INTEGER PRIMARY KEY CHECK (id = 1),
    ext_id      BIGINT NOT NULL,
    name        VARCHAR NOT NULL,
    created_at  TIMESTAMP,
    ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Operational state for this Synapse instance.
CREATE TABLE IF NOT EXISTS synapse_statistics (
    id                  INTEGER PRIMARY KEY CHECK (id = 1),
    started_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_reconciled_at  TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Runtime admin-configurable settings. Columns will be extended as the settings design matures.
CREATE TABLE IF NOT EXISTS synapse_settings (
    id                      INTEGER PRIMARY KEY CHECK (id = 1),
    primary_currency_name   VARCHAR NOT NULL DEFAULT 'Coins',
    secondary_currency_name VARCHAR NOT NULL DEFAULT 'Tokens',
    admin_role_ext_ids      TEXT,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Discord channel categories.
CREATE TABLE IF NOT EXISTS categories (
    id          INTEGER PRIMARY KEY,
    ext_id      BIGINT NOT NULL UNIQUE,
    name        VARCHAR NOT NULL,
    is_active   INTEGER NOT NULL DEFAULT 1,
    created_at  TIMESTAMP,
    ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Discord channels. type stores the JDA ChannelType name (TEXT, VOICE, STAGE, FORUM, NEWS, etc.).
CREATE TABLE IF NOT EXISTS channels (
    id          INTEGER PRIMARY KEY,
    ext_id      BIGINT NOT NULL UNIQUE,
    category_id BIGINT DEFAULT NULL,
    name        VARCHAR NOT NULL,
    type        VARCHAR NOT NULL,
    is_active   INTEGER NOT NULL DEFAULT 1,
    created_at  TIMESTAMP,
    ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- Discord threads: forum posts, text-channel threads, news-channel threads.
CREATE TABLE IF NOT EXISTS threads (
    id                      INTEGER PRIMARY KEY,
    ext_id                  BIGINT NOT NULL UNIQUE,
    channel_id              BIGINT NOT NULL,
    owner_ext_id            BIGINT,
    name                    VARCHAR NOT NULL,
    type                    VARCHAR NOT NULL,
    is_archived             INTEGER NOT NULL DEFAULT 0,
    is_locked               INTEGER NOT NULL DEFAULT 0,
    is_pinned               INTEGER NOT NULL DEFAULT 0,
    message_count           INTEGER NOT NULL DEFAULT 0,
    slowmode                INTEGER NOT NULL DEFAULT 0,
    auto_archive_duration   INTEGER NOT NULL DEFAULT 0,
    is_active               INTEGER NOT NULL DEFAULT 1,
    created_at              TIMESTAMP,
    ingested_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (channel_id) REFERENCES channels (id)
);

CREATE INDEX IF NOT EXISTS threads_channel_id_idx ON threads (channel_id);
CREATE INDEX IF NOT EXISTS threads_type_idx       ON threads (type);

-- Available tags defined on each ForumChannel.
CREATE TABLE IF NOT EXISTS forum_tags (
    id              INTEGER PRIMARY KEY,
    ext_id          BIGINT NOT NULL UNIQUE,
    channel_id      BIGINT NOT NULL,
    name            VARCHAR NOT NULL,
    emoji_name      VARCHAR,
    emoji_ext_id    BIGINT,
    is_moderated    INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP,
    ingested_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (channel_id) REFERENCES channels (id)
);

CREATE INDEX IF NOT EXISTS forum_tags_channel_id_idx ON forum_tags (channel_id);

-- Junction: which forum tags are applied to a thread (forum post).
CREATE TABLE IF NOT EXISTS thread_tags (
    thread_id       BIGINT NOT NULL,
    forum_tag_id    BIGINT NOT NULL,
    PRIMARY KEY (thread_id, forum_tag_id),
    FOREIGN KEY (thread_id)    REFERENCES threads (id),
    FOREIGN KEY (forum_tag_id) REFERENCES forum_tags (id)
);

-- Guild members.
CREATE TABLE IF NOT EXISTS members (
    id              INTEGER PRIMARY KEY,
    ext_id          BIGINT NOT NULL UNIQUE,
    name            VARCHAR NOT NULL,
    global_name     VARCHAR,
    nickname        VARCHAR,
    avatar_hash     VARCHAR,
    is_bot          INTEGER NOT NULL DEFAULT 0,
    is_active       INTEGER NOT NULL DEFAULT 1,
    joined_at       TIMESTAMP,
    premium_since   TIMESTAMP,
    pending         INTEGER NOT NULL DEFAULT 0,
    p_currency      INTEGER NOT NULL DEFAULT 0,
    level           INTEGER NOT NULL DEFAULT 1,
    s_currency      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Discord guild roles. Provides human-readable names for role snowflake IDs.
CREATE TABLE IF NOT EXISTS roles (
    id          INTEGER PRIMARY KEY,
    ext_id      BIGINT NOT NULL UNIQUE,
    name        VARCHAR NOT NULL,
    is_active   INTEGER NOT NULL DEFAULT 1,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Current role snapshot per member. Updated on GUILD_MEMBER_UPDATE and reconciliation.
CREATE TABLE IF NOT EXISTS member_roles (
    member_id   INTEGER NOT NULL,
    role_id     INTEGER NOT NULL,
    PRIMARY KEY (member_id, role_id),
    FOREIGN KEY (member_id) REFERENCES members (id),
    FOREIGN KEY (role_id)   REFERENCES roles (id)
);

-- Seasonal time windows for competitive periods.
CREATE TABLE IF NOT EXISTS seasons (
    id          INTEGER PRIMARY KEY,
    name        VARCHAR NOT NULL,
    starts_at   TIMESTAMP NOT NULL,
    ends_at     TIMESTAMP,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Aggregated stats per member per season.
CREATE TABLE IF NOT EXISTS seasonal_member_statistics (
    id              INTEGER PRIMARY KEY,
    member_id       BIGINT NOT NULL,
    season_id       BIGINT NOT NULL,
    messages_sent   INTEGER NOT NULL DEFAULT 0,
    reactions_sent  INTEGER NOT NULL DEFAULT 0,
    reactions_recv  INTEGER NOT NULL DEFAULT 0,
    threads_posted  INTEGER NOT NULL DEFAULT 0,
    voice_minutes   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id) REFERENCES members (id),
    FOREIGN KEY (season_id) REFERENCES seasons (id),
    UNIQUE      (member_id, season_id)
);

-- Every Discord event ingested by either scanner. No JSON. Child tables carry type-specific detail.
CREATE TABLE IF NOT EXISTS events (
    id          INTEGER PRIMARY KEY,
    member_id   BIGINT NOT NULL,
    channel_id  BIGINT,
    event_type  VARCHAR NOT NULL,
    created_at  TIMESTAMP,
    ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id)  REFERENCES members (id),
    FOREIGN KEY (channel_id) REFERENCES channels (id)
);

CREATE INDEX IF NOT EXISTS events_member_id_idx  ON events (member_id);
CREATE INDEX IF NOT EXISTS events_channel_id_idx ON events (channel_id);
CREATE INDEX IF NOT EXISTS events_event_type_idx ON events (event_type);
CREATE INDEX IF NOT EXISTS events_created_at_idx ON events (created_at);

-- Current state of each message. Edits UPSERT on ext_id — no edit history is kept.
CREATE TABLE IF NOT EXISTS messages (
    id                          INTEGER PRIMARY KEY,
    event_id                    BIGINT NOT NULL,
    ext_id                      BIGINT NOT NULL UNIQUE,
    thread_id                   BIGINT DEFAULT NULL,
    content                     TEXT,
    content_length              INTEGER NOT NULL DEFAULT 0,
    type                        INTEGER NOT NULL DEFAULT 0,
    is_reply                    INTEGER NOT NULL DEFAULT 0,
    referenced_message_ext_id   BIGINT,
    spawned_thread              INTEGER NOT NULL DEFAULT 0,
    edited_at                   TIMESTAMP,
    has_attachments             INTEGER NOT NULL DEFAULT 0,
    attachment_count            INTEGER NOT NULL DEFAULT 0,
    reaction_count              INTEGER NOT NULL DEFAULT 0,
    mention_user_count          INTEGER NOT NULL DEFAULT 0,
    mention_role_count          INTEGER NOT NULL DEFAULT 0,
    mention_channel_count       INTEGER NOT NULL DEFAULT 0,
    mention_everyone            INTEGER NOT NULL DEFAULT 0,
    is_tts                      INTEGER NOT NULL DEFAULT 0,
    is_pinned                   INTEGER NOT NULL DEFAULT 0,
    has_stickers                INTEGER NOT NULL DEFAULT 0,
    has_poll                    INTEGER NOT NULL DEFAULT 0,
    embed_count                 INTEGER NOT NULL DEFAULT 0,
    is_voice_message            INTEGER NOT NULL DEFAULT 0,
    flags                       BIGINT NOT NULL DEFAULT 0,
    author_is_bot               INTEGER NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP,
    ingested_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (event_id)   REFERENCES events (id),
    FOREIGN KEY (thread_id)  REFERENCES threads (id)
);

CREATE INDEX IF NOT EXISTS messages_ext_id_idx    ON messages (ext_id);
CREATE INDEX IF NOT EXISTS messages_event_id_idx  ON messages (event_id);
CREATE INDEX IF NOT EXISTS messages_type_idx      ON messages (type);
CREATE INDEX IF NOT EXISTS messages_thread_id_idx ON messages (thread_id);

-- Attachments belonging to a message.
CREATE TABLE IF NOT EXISTS message_attachments (
    id              INTEGER PRIMARY KEY,
    message_id      BIGINT NOT NULL,
    ext_id          BIGINT NOT NULL UNIQUE,
    filename        VARCHAR NOT NULL,
    description     TEXT,
    content_type    VARCHAR,
    size            INTEGER NOT NULL DEFAULT 0,
    width           INTEGER NOT NULL DEFAULT 0,
    height          INTEGER NOT NULL DEFAULT 0,
    duration_secs   REAL,
    FOREIGN KEY (message_id) REFERENCES messages (id)
);

CREATE INDEX IF NOT EXISTS message_attachments_msg_idx ON message_attachments (message_id);

-- Emoji reactions on a message. One row per distinct emoji.
CREATE TABLE IF NOT EXISTS message_reactions (
    id              INTEGER PRIMARY KEY,
    message_id      BIGINT NOT NULL,
    emoji_name      VARCHAR NOT NULL,
    emoji_ext_id    BIGINT,
    count           INTEGER NOT NULL DEFAULT 0,
    burst_count     INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (message_id) REFERENCES messages (id)
);

-- Expression index so COALESCE(emoji_ext_id, 0) collapses NULL → 0, treating
-- standard (non-custom) emoji as equal for uniqueness. Required because SQLite
-- treats NULLs as distinct in plain UNIQUE constraints. The DAO ON CONFLICT
-- clause mirrors this expression exactly.
CREATE UNIQUE INDEX IF NOT EXISTS message_reactions_uq
    ON message_reactions (message_id, emoji_name, COALESCE(emoji_ext_id, 0));

CREATE INDEX IF NOT EXISTS message_reactions_msg_idx ON message_reactions (message_id);

-- Role change detail for MEMBER_ROLE_CHANGE events. Stored as comma-separated ext_id lists.
CREATE TABLE IF NOT EXISTS member_role_change_events (
    id              INTEGER PRIMARY KEY,
    event_id        BIGINT NOT NULL,
    roles_added     TEXT,
    roles_removed   TEXT,
    FOREIGN KEY (event_id) REFERENCES events (id)
);

-- Voice session per member connection. left_at is NULL while connected.
-- Startup reconciliation closes orphaned sessions and re-opens for currently connected members.
CREATE TABLE IF NOT EXISTS voice_sessions (
    id              INTEGER PRIMARY KEY,
    event_id        BIGINT NOT NULL,
    member_id       BIGINT NOT NULL,
    channel_id      BIGINT NOT NULL,
    joined_at       TIMESTAMP NOT NULL,
    left_at         TIMESTAMP,
    duration_secs   REAL,
    FOREIGN KEY (event_id)   REFERENCES events (id),
    FOREIGN KEY (member_id)  REFERENCES members (id),
    FOREIGN KEY (channel_id) REFERENCES channels (id)
);

CREATE INDEX IF NOT EXISTS voice_sessions_member_idx ON voice_sessions (member_id);
CREATE INDEX IF NOT EXISTS voice_sessions_open_idx   ON voice_sessions (left_at) WHERE left_at IS NULL;

-- Rules: named evaluation targets with event scoping, cooldown, and toggles.
CREATE TABLE IF NOT EXISTS rules (
    id                INTEGER PRIMARY KEY,
    name              VARCHAR NOT NULL UNIQUE,
    description       TEXT,
    event_type        VARCHAR NOT NULL,
    enabled           INTEGER NOT NULL DEFAULT 0,
    applies_live      INTEGER NOT NULL DEFAULT 1,
    applies_historic  INTEGER NOT NULL DEFAULT 0,
    cooldown_seconds  INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Parameterized predicates attached to a rule. All must pass (AND logic).
CREATE TABLE IF NOT EXISTS rule_predicates (
    id              INTEGER PRIMARY KEY,
    rule_id         BIGINT NOT NULL,
    predicate_type  VARCHAR NOT NULL,
    parameters      TEXT,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (rule_id) REFERENCES rules (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS rule_predicates_rule_idx ON rule_predicates (rule_id);

-- Outcomes dispatched when a rule fires. One rule can have many outcomes.
CREATE TABLE IF NOT EXISTS rule_outcomes (
    id          INTEGER PRIMARY KEY,
    rule_id     BIGINT NOT NULL,
    type        VARCHAR NOT NULL,
    p_currency  INTEGER,
    s_currency  INTEGER,
    parameters  TEXT,
    FOREIGN KEY (rule_id) REFERENCES rules (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS rule_outcomes_rule_idx ON rule_outcomes (rule_id);

-- Deduplication and cooldown log. One row per rule+event pair that fired.
CREATE TABLE IF NOT EXISTS rule_evaluations (
    id         INTEGER PRIMARY KEY,
    rule_id    BIGINT NOT NULL,
    event_id   BIGINT NOT NULL,
    member_id  BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (rule_id, event_id),
    FOREIGN KEY (rule_id)  REFERENCES rules (id),
    FOREIGN KEY (event_id) REFERENCES events (id),
    FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE INDEX IF NOT EXISTS rule_evaluations_rule_member_idx ON rule_evaluations (rule_id, member_id);
CREATE INDEX IF NOT EXISTS rule_evaluations_event_idx ON rule_evaluations (event_id);
