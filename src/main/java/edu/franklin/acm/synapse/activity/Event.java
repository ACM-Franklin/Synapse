package edu.franklin.acm.synapse.activity;

import java.time.LocalDateTime;

/**
 * A single entry in the Event Lake — the lean parent row.
 * Type-specific child tables (message_events, member_role_change_events, etc.)
 * carry the detail. No JSON blobs. No currency columns.
 */
public record Event(
        long id,
        long memberId,
        Long channelId,
        String eventType,
        LocalDateTime createdAt) {
}
