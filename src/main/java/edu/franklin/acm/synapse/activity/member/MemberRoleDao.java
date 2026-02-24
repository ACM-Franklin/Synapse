package edu.franklin.acm.synapse.activity.member;

import java.util.List;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Manages the member_roles junction table and tracks role change events.
 * 
 * <p><strong>Workflow:</strong> To replace a member's entire role set, call
 * {@link #deleteRoles(long)} followed by zero or more {@link #insertRole(long, long)}
 * calls, then {@link #insertRoleChangeEvent(MemberRoleChangeEvent)} to record the change.
 */
public interface MemberRoleDao {
    
    /**
     * Deletes all roles assigned to the specified member.
     * 
     * @param memberId the member's internal ID
     */
    @SqlUpdate("DELETE FROM member_roles WHERE member_id = :memberId")
    void deleteRoles(@Bind("memberId") long memberId);

    /**
     * Inserts a single role assignment for a member.
     * 
     * <p>Gracefully ignores conflicts (role already assigned).
     * 
     * @param memberId  the member's internal ID
     * @param roleExtId the external role ID (Discord snowflake)
     */
    @SqlUpdate("""
        INSERT INTO member_roles (member_id, role_ext_id)
        VALUES (:memberId, :roleExtId)
        ON CONFLICT DO NOTHING
        """)
    void insertRole(@Bind("memberId") long memberId, @Bind("roleExtId") long roleExtId);

    /**
     * Retrieves all roles currently assigned to a member.
     * 
     * @param memberId the member's internal ID
     * @return list of external role IDs (Discord snowflakes)
     */
    @SqlQuery("SELECT role_ext_id FROM member_roles WHERE member_id = :memberId")
    List<Long> findRolesByMemberId(@Bind("memberId") long memberId);

    /**
     * Records a role assignment change event.
     * 
     * @param event contains event ID, added roles, and removed roles
     */
    @SqlUpdate("""
        INSERT INTO member_role_change_events (event_id, roles_added, roles_removed)
        VALUES (:eventId, :rolesAdded, :rolesRemoved)
        """)
    void insertRoleChangeEvent(@BindMethods MemberRoleChangeEvent event);
}
