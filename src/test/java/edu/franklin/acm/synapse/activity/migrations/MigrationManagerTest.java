package edu.franklin.acm.synapse.activity.migrations;

import java.nio.file.Path;
import java.util.Set;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.test.JdbiTestSupport;

class MigrationManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void freshDatabaseUsesSnapshotWithoutReplayingUpgradeScripts() {
        Jdbi jdbi = blankSqlite(tempDir.resolve("fresh.sqlite"));
        MigrationDao dao = jdbi.onDemand(MigrationDao.class);

        assertDoesNotThrow(() -> new MigrationManager(
                jdbi,
                dao,
                true,
                "/schemas/synapse.sql",
                "/schemas/migrations/"));

        Set<String> tables = JdbiTestSupport.queryStringSet(jdbi, "SELECT name FROM sqlite_master WHERE type = 'table'");
        Set<String> messageColumns = JdbiTestSupport.queryStringSet(jdbi, "SELECT name FROM pragma_table_info('messages')");
        Set<String> rewardLedgerColumns = JdbiTestSupport.queryStringSet(jdbi, "SELECT name FROM pragma_table_info('reward_ledger')");
        Set<String> successfulMigrations = JdbiTestSupport.queryStringSet(
                jdbi,
                "SELECT name FROM migrations WHERE succeeded = 1");

        assertAll(
                () -> assertTrue(tables.contains("historical_scan_jobs")),
                () -> assertTrue(tables.contains("historical_scan_checkpoints")),
                () -> assertTrue(messageColumns.contains("is_deleted")),
                () -> assertTrue(messageColumns.contains("deleted_at")),
                () -> assertTrue(rewardLedgerColumns.contains("subject_type")),
                () -> assertTrue(rewardLedgerColumns.contains("subject_ext_id")),
                () -> assertTrue(successfulMigrations.contains("/schemas/migrations/1_historical_scan_state.sql")),
                () -> assertTrue(successfulMigrations.contains("/schemas/migrations/2_message_delete_state.sql")),
                () -> assertTrue(successfulMigrations.contains("/schemas/migrations/3_reward_subject_accounting.sql")));
    }

    @Test
    void existingDatabaseRunsMissingUpgradeScriptsWithoutSnapshotReplay() {
        Jdbi jdbi = blankSqlite(tempDir.resolve("legacy.sqlite"));
        seedLegacyDatabase(jdbi);
        MigrationDao dao = jdbi.onDemand(MigrationDao.class);

        assertDoesNotThrow(() -> new MigrationManager(
                jdbi,
                dao,
                true,
                "/schemas/synapse.sql",
                "/schemas/migrations/"));

        Set<String> tables = JdbiTestSupport.queryStringSet(jdbi, "SELECT name FROM sqlite_master WHERE type = 'table'");
        Set<String> indexes = JdbiTestSupport.queryStringSet(jdbi, "SELECT name FROM sqlite_master WHERE type = 'index'");
        Set<String> messageColumns = JdbiTestSupport.queryStringSet(jdbi, "SELECT name FROM pragma_table_info('messages')");
        Set<String> rewardLedgerColumns = JdbiTestSupport.queryStringSet(jdbi, "SELECT name FROM pragma_table_info('reward_ledger')");
        Set<String> successfulMigrations = JdbiTestSupport.queryStringSet(
                jdbi,
                "SELECT name FROM migrations WHERE succeeded = 1");

        assertAll(
                () -> assertTrue(tables.contains("historical_scan_jobs")),
                () -> assertTrue(tables.contains("historical_scan_checkpoints")),
                () -> assertTrue(messageColumns.contains("is_deleted")),
                () -> assertTrue(messageColumns.contains("deleted_at")),
                () -> assertTrue(rewardLedgerColumns.contains("subject_type")),
                () -> assertTrue(rewardLedgerColumns.contains("subject_ext_id")),
                () -> assertTrue(indexes.contains("messages_is_deleted_idx")),
                () -> assertTrue(indexes.contains("reward_ledger_subject_idx")),
                () -> assertTrue(successfulMigrations.contains("/schemas/migrations/1_historical_scan_state.sql")),
                () -> assertTrue(successfulMigrations.contains("/schemas/migrations/2_message_delete_state.sql")),
                () -> assertTrue(successfulMigrations.contains("/schemas/migrations/3_reward_subject_accounting.sql")));
    }

    private static Jdbi blankSqlite(Path databasePath) {
        Jdbi jdbi = Jdbi.create("jdbc:sqlite:" + databasePath.toAbsolutePath());
        jdbi.installPlugin(new SqlObjectPlugin());
        return jdbi;
    }

    private static void seedLegacyDatabase(Jdbi jdbi) {
        jdbi.useHandle(handle -> handle.createScript("""
                CREATE TABLE migrations (
                    id          INTEGER NOT NULL PRIMARY KEY,
                    name        VARCHAR NOT NULL,
                    succeeded   BOOLEAN NOT NULL,
                    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE messages (
                    id                        INTEGER PRIMARY KEY,
                    event_id                  BIGINT NOT NULL,
                    ext_id                    BIGINT NOT NULL UNIQUE,
                    thread_id                 BIGINT DEFAULT NULL,
                    content                   TEXT,
                    content_length            INTEGER NOT NULL DEFAULT 0,
                    type                      INTEGER NOT NULL DEFAULT 0,
                    is_reply                  INTEGER NOT NULL DEFAULT 0,
                    referenced_message_ext_id BIGINT,
                    spawned_thread            INTEGER NOT NULL DEFAULT 0,
                    edited_at                 TIMESTAMP,
                    has_attachments           INTEGER NOT NULL DEFAULT 0,
                    attachment_count          INTEGER NOT NULL DEFAULT 0,
                    reaction_count            INTEGER NOT NULL DEFAULT 0,
                    mention_user_count        INTEGER NOT NULL DEFAULT 0,
                    mention_role_count        INTEGER NOT NULL DEFAULT 0,
                    mention_channel_count     INTEGER NOT NULL DEFAULT 0,
                    mention_everyone          INTEGER NOT NULL DEFAULT 0,
                    is_tts                    INTEGER NOT NULL DEFAULT 0,
                    is_pinned                 INTEGER NOT NULL DEFAULT 0,
                    has_stickers              INTEGER NOT NULL DEFAULT 0,
                    has_poll                  INTEGER NOT NULL DEFAULT 0,
                    embed_count               INTEGER NOT NULL DEFAULT 0,
                    is_voice_message          INTEGER NOT NULL DEFAULT 0,
                    flags                     BIGINT NOT NULL DEFAULT 0,
                    author_is_bot             INTEGER NOT NULL DEFAULT 0,
                    created_at                TIMESTAMP,
                    ingested_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE reward_ledger (
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
                    created_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """).execute());
    }
}