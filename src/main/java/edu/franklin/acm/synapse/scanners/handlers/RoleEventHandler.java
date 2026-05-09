package edu.franklin.acm.synapse.scanners.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.member.MemberRoleDao;
import edu.franklin.acm.synapse.activity.member.RoleDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Role;

@ApplicationScoped
public class RoleEventHandler {

    private static final Logger log = LoggerFactory.getLogger(RoleEventHandler.class);

    @Inject RoleDao roleDao;
    @Inject MemberRoleDao memberRoleDao;

    public void handleCreate(Role role) {
        upsertRole(role.getIdLong(), role.getName());
    }

    public void handleNameUpdate(Role role) {
        upsertRole(role.getIdLong(), role.getName());
    }

    public void handleDelete(Role role) {
        deleteRole(role.getIdLong());
    }

    void upsertRole(long roleExtId, String name) {
        roleDao.upsert(roleExtId, name);
        log.debug("Upserted live role {} ({})", name, roleExtId);
    }

    void deleteRole(long roleExtId) {
        memberRoleDao.deleteRoleAssignmentsByRoleExtId(roleExtId);
        roleDao.markInactive(roleExtId);
        log.debug("Marked live role {} inactive and cleared member assignments", roleExtId);
    }
}