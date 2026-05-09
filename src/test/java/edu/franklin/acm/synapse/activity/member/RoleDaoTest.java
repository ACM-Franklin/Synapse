package edu.franklin.acm.synapse.activity.member;

import java.nio.file.Path;
import java.util.Map;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.test.JdbiTestSupport;

class RoleDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void upsertUpdatesNameAndReactivatesExistingRole() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        RoleDao dao = JdbiTestSupport.dao(jdbi, RoleDao.class);

        long createdId = dao.upsert(1001L, "old-name");
        dao.markInactive(1001L);
        long updatedId = dao.upsert(1001L, "new-name");

        Map<String, Object> row = JdbiTestSupport.queryRow(jdbi, """
                SELECT id, name, is_active FROM roles WHERE ext_id = :extId
                """, "extId", 1001L);

        assertAll(
                () -> assertEquals(createdId, updatedId),
                () -> assertEquals(createdId, longValue(row, "id")),
                () -> assertEquals("new-name", row.get("name")),
                () -> assertEquals(1, intValue(row, "is_active")),
                () -> assertTrue(dao.findAllActiveExtIds().contains(1001L)));
    }

    @Test
    void markInactiveOnlyDeactivatesTheTargetRole() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        RoleDao dao = JdbiTestSupport.dao(jdbi, RoleDao.class);
        dao.upsert(1002L, "target");
        dao.upsert(1003L, "other");

        dao.markInactive(1002L);

        assertAll(
                () -> assertEquals(0, activeFlag(jdbi, 1002L)),
                () -> assertEquals(1, activeFlag(jdbi, 1003L)));
    }

    private static int activeFlag(Jdbi jdbi, long extId) {
        return JdbiTestSupport.queryInt(jdbi, """
                SELECT is_active FROM roles WHERE ext_id = :extId
                """, "extId", extId);
    }

    private static int intValue(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }

    private static long longValue(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}