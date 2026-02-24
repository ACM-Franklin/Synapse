package edu.franklin.acm.synapse.activity.voice;

import java.time.LocalDateTime;

/**
 * One voice session — a member's time in a voice/stage channel.
 * left_at is null while the member is still connected.
 * duration_secs is computed when the session ends.
 */
public record VoiceSession(
        long id,
        long eventId,
        long memberId,
        long channelId,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        Double durationSecs) {
}
