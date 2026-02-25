-- ============================================================================
-- Synapse Schema — Single-Guild Model
-- ============================================================================
-- One instance of Synapse = one guild. The guild metadata lives in a single
-- config row. Everything else is implicitly scoped to this guild.
--
-- Design principles:
--   * No JSON blobs. All queryable data lives in real SQL columns.
--   * Events are a lean parent table. Child tables hold type-specific data.
--   * Message edits UPSERT — only the current state matters.
--   * Rewards are derived by the rule engine, never stored on events.
-- ============================================================================

-- Tracks schema migrations applied to this database.
CREATE TABLE IF NOT EXISTS migrations (
    id          INTEGER NOT NULL PRIMARY KEY,
    name        VARCHAR NOT NULL,
    succeeded   BOOLEAN NOT NULL,
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Metadata about the guild this instance manages.
CREATE TABLE IF NOT EXISTS guild_metadata (
    id         INTEGER PRIMARY KEY CHECK (id = 1),
    ext_id     BIGINT NOT NULL,
    name       VARCHAR NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bot uptime / heartbeat tracking.
CREATE TABLE IF NOT EXISTS bot_statistics (
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Discord channel categories.
CREATE TABLE IF NOT EXISTS categories (
    id          INTEGER PRIMARY KEY,
    ext_id      BIGINT NOT NULL UNIQUE,
    name        VARCHAR NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Discord text channels.
CREATE TABLE IF NOT EXISTS channels (
    id          INTEGER PRIMARY KEY,
    ext_id      BIGINT NOT NULL UNIQUE,
    category_id BIGINT DEFAULT NULL,
    name        VARCHAR NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- Guild members.
-- Live scanner maintains currency for name, avatar_hash, pending, nickname.
-- joined_at and premium_since are captured at first-seen and updated on member events.
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

-- Junction table for member roles. Current snapshot — updated on GUILD_MEMBER_UPDATE.
-- Used to diff role changes for role transition events.
CREATE TABLE IF NOT EXISTS member_roles (
    member_id   INTEGER NOT NULL,
    role_ext_id BIGINT  NOT NULL,
    PRIMARY KEY (member_id, role_ext_id),
    FOREIGN KEY (member_id) REFERENCES members (id)
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

-- ============================================================================
-- Event Lake — Lean Parent Table
-- ============================================================================
-- Every Discord event ingested by either scanner lands here.
-- No JSON. No currency columns. Type-specific child tables carry the detail.
CREATE TABLE IF NOT EXISTS events (
    id          INTEGER PRIMARY KEY,
    member_id   BIGINT NOT NULL,
    channel_id  BIGINT,
    event_type  VARCHAR NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id)  REFERENCES members (id),
    FOREIGN KEY (channel_id) REFERENCES channels (id)
);

CREATE INDEX IF NOT EXISTS events_member_id_idx ON events (member_id);
CREATE INDEX IF NOT EXISTS events_channel_id_idx ON events (channel_id);
CREATE INDEX IF NOT EXISTS events_event_type_idx ON events (event_type);
CREATE INDEX IF NOT EXISTS events_created_at_idx ON events (created_at);

-- ============================================================================
-- Message Events — Normalized child of events
-- ============================================================================
-- One row per message. Edits UPSERT (ON CONFLICT ext_id) so only current
-- state is stored. No edit history — deliberately.
CREATE TABLE IF NOT EXISTS message_events (
    id                          INTEGER PRIMARY KEY,
    event_id                    BIGINT NOT NULL,
    ext_id                      BIGINT NOT NULL UNIQUE,
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
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (event_id) REFERENCES events (id)
);

CREATE INDEX IF NOT EXISTS message_events_ext_id_idx ON message_events (ext_id);
CREATE INDEX IF NOT EXISTS message_events_event_id_idx ON message_events (event_id);
CREATE INDEX IF NOT EXISTS message_events_type_idx ON message_events (type);

-- ============================================================================
-- Message Attachments — one row per attachment on a message
-- ============================================================================
CREATE TABLE IF NOT EXISTS message_attachments (
    id                  INTEGER PRIMARY KEY,
    message_event_id    BIGINT NOT NULL,
    ext_id              BIGINT NOT NULL UNIQUE,
    filename            VARCHAR NOT NULL,
    description         TEXT,
    content_type        VARCHAR,
    size                INTEGER NOT NULL DEFAULT 0,
    width               INTEGER NOT NULL DEFAULT 0,
    height              INTEGER NOT NULL DEFAULT 0,
    duration_secs       REAL,
    FOREIGN KEY (message_event_id) REFERENCES message_events (id)
);

CREATE INDEX IF NOT EXISTS message_attachments_msg_idx ON message_attachments (message_event_id);

-- ============================================================================
-- Message Reactions — one row per distinct emoji reaction on a message
-- ============================================================================
CREATE TABLE IF NOT EXISTS message_reactions (
    id                  INTEGER PRIMARY KEY,
    message_event_id    BIGINT NOT NULL,
    emoji_name          VARCHAR NOT NULL,
    emoji_ext_id        BIGINT,
    count               INTEGER NOT NULL DEFAULT 0,
    burst_count         INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (message_event_id) REFERENCES message_events (id),
    UNIQUE (message_event_id, emoji_name, emoji_ext_id)
);

CREATE INDEX IF NOT EXISTS message_reactions_msg_idx ON message_reactions (message_event_id);

-- ============================================================================
-- Member Role Change Events — child of events for MEMBER_ROLE_CHANGE type
-- ============================================================================
-- Captures a diff: which roles were added and which were removed.
-- Stored as comma-separated ext_id lists (simple, queryable enough for our needs).
CREATE TABLE IF NOT EXISTS member_role_change_events (
    id              INTEGER PRIMARY KEY,
    event_id        BIGINT NOT NULL,
    roles_added     TEXT,
    roles_removed   TEXT,
    FOREIGN KEY (event_id) REFERENCES events (id)
);

-- ============================================================================
-- Voice Sessions — tracks time spent in voice/stage channels
-- ============================================================================
-- One row per voice connection. left_at is NULL while connected.
-- Startup reconciliation closes orphaned sessions (left_at IS NULL) using
-- guild.getVoiceStates() to check who's actually still connected.
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
CREATE INDEX IF NOT EXISTS voice_sessions_open_idx ON voice_sessions (left_at) WHERE left_at IS NULL;
