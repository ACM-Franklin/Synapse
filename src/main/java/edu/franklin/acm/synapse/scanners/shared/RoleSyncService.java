package edu.franklin.acm.synapse.scanners.shared;

import edu.franklin.acm.synapse.activity.member.MemberRoleDao;
import edu.franklin.acm.synapse.activity.member.RoleDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * Synchronizes a member's role snapshot. Upserts each role into the
 * {@code roles} reference table (capturing name), then replaces the
 * {@code member_roles} junction rows.
 */
@ApplicationScoped
public class RoleSyncService {

    @Inject RoleDao roleDao;
    @Inject MemberRoleDao memberRoleDao;

    /**
     * Replaces the member_roles junction table rows with the member's current roles.
     * Each role is first upserted into the {@code roles} reference table so its
     * human-readable name is always current.
     */
    public void syncRoles(long memberId, Member member) {
        memberRoleDao.deleteRoles(memberId);
        for (Role role : member.getRoles()) {
            long roleId = roleDao.upsert(role.getIdLong(), role.getName());
            memberRoleDao.insertRole(memberId, roleId);
        }
    }
}
