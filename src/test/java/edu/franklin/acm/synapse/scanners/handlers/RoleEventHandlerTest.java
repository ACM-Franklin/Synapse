package edu.franklin.acm.synapse.scanners.handlers;

import java.nio.file.Path;
import java.util.Map;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.activity.member.MemberRoleDao;
import edu.franklin.acm.synapse.activity.member.RoleDao;
import edu.franklin.acm.synapse.test.JdbiTestSupport;

class RoleEventHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void upsertRoleCreatesRenamesAndReactivatesRoleSnapshot() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        RoleEventHandler handler = roleHandler(jdbi);

        handler.upsertRole(4001L, "old-name");
        handler.deleteRole(4001L);
        handler.upsertRole(4001L, "new-name");

        Map<String, Object> row = JdbiTestSupport.queryRow(jdbi, """
                SELECT name, is_active FROM roles WHERE ext_id = :extId
                """, "extId", 4001L);

        assertAll(
                () -> assertEquals("new-name", row.get("name")),
                () -> assertEquals(1, intValue(row, "is_active")));
    }

    @Test
    void deleteRoleMarksRoleInactiveAndClearsCurrentAssignments() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        RoleEventHandler handler = roleHandler(jdbi);
        RoleDao roleDao = JdbiTestSupport.dao(jdbi, RoleDao.class);
        MemberRoleDao memberRoleDao = JdbiTestSupport.dao(jdbi, MemberRoleDao.class);
        long memberId = JdbiTestSupport.insertMember(jdbi, 5001L);
        long roleId = roleDao.upsert(5002L, "doomed");
        memberRoleDao.insertRole(memberId, roleId);

        handler.deleteRole(5002L);

        assertAll(
                () -> assertEquals(0, JdbiTestSupport.queryInt(jdbi, """
                        SELECT is_active FROM roles WHERE ext_id = :extId
                        """, "extId", 5002L)),
                () -> assertEquals(0, JdbiTestSupport.queryInt(jdbi, """
                        SELECT COUNT(*) FROM member_roles WHERE role_id = :roleId
                        """, "roleId", roleId)));
    }

    private static RoleEventHandler roleHandler(Jdbi jdbi) {
        RoleEventHandler handler = new RoleEventHandler();
        handler.roleDao = JdbiTestSupport.dao(jdbi, RoleDao.class);
        handler.memberRoleDao = JdbiTestSupport.dao(jdbi, MemberRoleDao.class);
        return handler;
    }

    private static int intValue(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }
}