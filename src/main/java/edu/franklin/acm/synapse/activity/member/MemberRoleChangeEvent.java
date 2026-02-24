package edu.franklin.acm.synapse.activity.member;

/**
 * Records a role transition diff for a member. FK to events.
 * roles_added and roles_removed are comma-separated external role IDs.
 */
public record MemberRoleChangeEvent(
        long id,
        long eventId,
        String rolesAdded,
        String rolesRemoved) {
}
