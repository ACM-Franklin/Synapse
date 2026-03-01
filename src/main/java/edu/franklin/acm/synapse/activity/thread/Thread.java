package edu.franklin.acm.synapse.activity.thread;

import java.time.LocalDateTime;

/**
 * Normalized representation of a Discord thread in the database. Covers forum
 * posts, text channel threads, and news channel threads.
 */
public record Thread(
        long id,
        long extId,
        long channelId,
        Long ownerExtId,
        String name,
        String type,
        boolean isArchived,
        boolean isLocked,
        boolean isPinned,
        int messageCount,
        int slowmode,
        int autoArchiveDuration,
        boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
