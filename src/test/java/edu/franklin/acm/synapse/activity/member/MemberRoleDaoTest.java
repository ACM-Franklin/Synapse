package edu.franklin.acm.synapse.activity.member;

import java.nio.file.Path;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.test.JdbiTestSupport;

class MemberRoleDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteRoleAssignmentsByRoleExtIdRemovesOnlyThatRole() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        long memberId = JdbiTestSupport.insertMember(jdbi, 2001L);
        long secondMemberId = JdbiTestSupport.insertMember(jdbi, 2002L);
        RoleDao roleDao = JdbiTestSupport.dao(jdbi, RoleDao.class);
        MemberRoleDao memberRoleDao = JdbiTestSupport.dao(jdbi, MemberRoleDao.class);
        long removedRoleId = roleDao.upsert(3001L, "removed");
        long retainedRoleId = roleDao.upsert(3002L, "retained");
        memberRoleDao.insertRole(memberId, removedRoleId);
        memberRoleDao.insertRole(memberId, retainedRoleId);
        memberRoleDao.insertRole(secondMemberId, removedRoleId);

        memberRoleDao.deleteRoleAssignmentsByRoleExtId(3001L);

        assertAll(
                () -> assertEquals(0, assignmentCount(jdbi, removedRoleId)),
                () -> assertEquals(1, assignmentCount(jdbi, retainedRoleId)),
                () -> assertEquals(1, memberRoleDao.findRoleExtIdsByMemberId(memberId).size()),
                () -> assertEquals(3002L, memberRoleDao.findRoleExtIdsByMemberId(memberId).get(0)));
    }

    private static int assignmentCount(Jdbi jdbi, long roleId) {
        return JdbiTestSupport.queryInt(jdbi, """
                SELECT COUNT(*) FROM member_roles WHERE role_id = :roleId
                """, "roleId", roleId);
    }
}