package edu.franklin.acm.synapse.activity;

/**
 * A single entry in the Event Lake — the lean parent row.
 * Type-specific child tables (message_events, member_role_change_events, etc.)
 */
public record Event(
        long id,
        long memberId,
        Long channelId,
        String eventType,
        String createdAt) {
}
