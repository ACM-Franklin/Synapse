package edu.franklin.acm.synapse.activity;

import java.nio.file.Path;
import java.util.Set;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.test.JdbiTestSupport;

class SchemaSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void schemaContainsRewardLedgerAndMessageMutationSupport() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);

        Set<String> tables = JdbiTestSupport.queryStringSet(jdbi, """
                SELECT name FROM sqlite_master WHERE type = 'table'
                """);
        Set<String> messageColumns = JdbiTestSupport.queryStringSet(jdbi, """
                SELECT name FROM pragma_table_info('messages')
                """);
        Set<String> indexes = JdbiTestSupport.queryStringSet(jdbi, """
                SELECT name FROM sqlite_master WHERE type = 'index'
                """);

        assertAll(
                () -> assertTrue(tables.contains("reward_ledger")),
                () -> assertTrue(messageColumns.contains("is_deleted")),
                () -> assertTrue(messageColumns.contains("deleted_at")),
                () -> assertTrue(indexes.contains("message_reactions_uq")),
                () -> assertTrue(indexes.contains("reward_ledger_award_uq")));
    }
}
