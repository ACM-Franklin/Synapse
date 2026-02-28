package edu.franklin.acm.synapse.scanners.shared;

import java.util.Set;
import java.util.stream.Collectors;

import edu.franklin.acm.synapse.activity.member.MemberRoleDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;

@ApplicationScoped
public class RoleSyncService {

    @Inject MemberRoleDao memberRoleDao;

    /**
     * Replaces the member_roles junction table rows with the member's current roles.
     */
    public void syncRoles(long memberId, Member member) {
        Set<Long> currentRoleExtIds = member.getRoles().stream()
                .map(ISnowflake::getIdLong)
                .collect(Collectors.toSet());

        memberRoleDao.deleteRoles(memberId);
        for (long roleExtId : currentRoleExtIds) {
            memberRoleDao.insertRole(memberId, roleExtId);
        }
    }
}
