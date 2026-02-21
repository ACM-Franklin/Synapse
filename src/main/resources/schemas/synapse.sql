CREATE TABLE migrations (
    id          INTEGER PRIMARY KEY,
    name        VARCHAR NOT NULL,
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bot_statistics (
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE guilds (
    id         INTEGER PRIMARY KEY,
    ext_id     VARCHAR NOT NULL,
    name       VARCHAR NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)

CREATE TABLE categories (
    id          INTEGER PRIMARY KEY,
    ext_id      VARCHAR NOT NULL,
    name        VARCHAR NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)

CREATE TABLE channels (
    id          INTEGER PRIMARY KEY,
    ext_id      VARCHAR NOT NULL,
    category_id INTEGER DEFAULT NULL,
    name        VARCHAR NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE TABLE users (
    id          INTEGER PRIMARY KEY,
    ext_id      VARCHAR NOT NULL,
    name        VARCHAR NOT NULL,
    experience  INTEGER NOT NULL DEFAULT 0,
    level       INTEGER NOT NULL DEFAULT 1,
    gold        INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE members (
    id          INTEGER PRIMARY KEY,
    guild_id    INTEGER,
    user_id     INTEGER,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (guild_id) REFERENCES guilds (id),
    FOREIGN KEY (user_id)  REFERENCES users (id),
    UNIQUE      (guild_id, user_id)
);

CREATE TABLE seasons (
    id          INTEGER PRIMARY KEY,
    guild_id    INTEGER NOT NULL,
    name        VARCHAR NOT NULL,
    starts_at   TIMESTAMP NOT NULL,
    ends_at     TIMESTAMP,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (guild_id) REFERENCES guilds (id)
);

CREATE TABLE seasonal_user_statistics (
    id              INTEGER PRIMARY KEY,
    user_id         INTEGER NOT NULL,
    guild_id        INTEGER NOT NULL,
    messages_sent   INTEGER NOT NULL DEFAULT 0,
    reactions_sent  INTEGER NOT NULL DEFAULT 0,
    reactions_recv  INTEGER NOT NULL DEFAULT 0,
    threads_posted  INTEGER NOT NULL DEFAULT 0,
    voice_minutes   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (guild_id) REFERENCES guilds (id),
    FOREIGN KEY (user_id)  REFERENCES users (id),
    UNIQUE      (user_id, guild_id)
);

CREATE TABLE user_activity (
    id                  INTEGER PRIMARY KEY,
    user_id             INTEGER NOT NULL,
    guild_id            INTEGER,
    event_type          VARCHAR NOT NULL,
    metadata            JSON NOT NULL,
    awarded_experience  INTEGER NOT NULL DEFAULT 0,
    awarded_gold        INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (guild_id) REFERENCES guilds (id),
    FOREIGN KEY (user_id)  REFERENCES users (id)
);
CREATE INDEX user_activity_user_guild_id_idx ON user_activity (user_id, guild_id);