package edu.franklin.acm.synapse.api.dto;

/**
 * Returned by GET /api/auth/me. {@code userId} is the Discord user snowflake
 * as a string (JS loses precision on >2^53 longs).
 */
public record CurrentUserDto(
        String userId,
        String username,
        String globalName,
        String avatarHash,
        boolean isMember,
        boolean isAdmin) {
}
