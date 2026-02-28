package edu.franklin.acm.synapse.rules.engine;

import edu.franklin.acm.synapse.activity.message.MessageEvent;

/**
 * Carries all evaluable fields for rule predicate evaluation.
 * Built from event data at the point of evaluation. Null fields
 * indicate the data is not applicable for this event type.
 */
public record RuleContext(
        // Event metadata
        String eventType,
        long eventId,
        long memberId,
        Long channelId,

        // Message fields (null for non-message events)
        Integer contentLength,
        Boolean authorIsBot,
        Boolean isReply,
        Boolean hasPoll,
        Boolean hasStickers,
        Boolean isTts,
        Boolean isPinned,
        Boolean hasAttachments,
        Integer attachmentCount,
        Integer reactionCount,
        Integer mentionUserCount,
        Boolean mentionEveryone,
        Integer embedCount,
        Boolean isVoiceMessage,
        Integer messageType,

        // Attachment fields (from first attachment, null if none)
        String attachmentFilename,
        String attachmentContentType,

        // Member state
        Long memberExtId,
        Boolean memberIsBoosting,
        String memberJoinedAt,
        Integer memberPCurrency,
        Integer memberSCurrency,

        // Channel state
        Long channelExtId,
        String channelType,
        Long categoryExtId,

        // Role change fields (null for non-role events)
        String rolesAdded,
        String rolesRemoved,

        // Voice fields (null for non-voice events)
        Long voiceChannelExtId,
        Double sessionDurationMinutes,

        // Timestamp
        String createdAt) {

    /**
     * Resolves a named boolean field from this context.
     */
    public Boolean getBooleanField(String fieldName) {
        return switch (fieldName) {
            case "author_is_bot" -> authorIsBot;
            case "is_reply" -> isReply;
            case "has_poll" -> hasPoll;
            case "has_stickers" -> hasStickers;
            case "is_tts" -> isTts;
            case "is_pinned" -> isPinned;
            case "has_attachments" -> hasAttachments;
            case "mention_everyone" -> mentionEveryone;
            case "is_voice_message" -> isVoiceMessage;
            case "has_embed" -> embedCount != null && embedCount > 0;
            case "member_is_boosting" -> memberIsBoosting;
            default -> null;
        };
    }

    /**
     * Resolves a named numeric field from this context.
     */
    public Number getNumericField(String fieldName) {
        return switch (fieldName) {
            case "content_length" -> contentLength;
            case "attachment_count" -> attachmentCount;
            case "reaction_count" -> reactionCount;
            case "mention_user_count" -> mentionUserCount;
            case "embed_count" -> embedCount;
            case "message_type" -> messageType;
            case "p_currency" -> memberPCurrency;
            case "s_currency" -> memberSCurrency;
            case "attachment_size" -> null; // would need attachment size on context
            case "session_duration_minutes" -> sessionDurationMinutes;
            default -> null;
        };
    }

    /**
     * Resolves a named string field from this context.
     */
    public String getStringField(String fieldName) {
        return switch (fieldName) {
            case "channel_ext_id" -> channelExtId != null ? channelExtId.toString() : null;
            case "channel_type" -> channelType;
            case "category_ext_id" -> categoryExtId != null ? categoryExtId.toString() : null;
            case "message_type" -> messageType != null ? messageType.toString() : null;
            case "attachment_filename" -> attachmentFilename;
            case "attachment_content_type" -> attachmentContentType;
            case "voice_channel_ext_id" -> voiceChannelExtId != null ? voiceChannelExtId.toString() : null;
            default -> null;
        };
    }

    /**
     * Build a context for a MESSAGE_CREATE event.
     */
    public static RuleContext forMessage(long eventId, long memberId, Long channelId,
                                         MessageEvent msg, Long memberExtId,
                                         boolean memberIsBoosting, String memberJoinedAt,
                                         int memberPCurrency, int memberSCurrency,
                                         Long channelExtId, String channelType, Long categoryExtId,
                                         String attachmentFilename, String attachmentContentType) {
        return new RuleContext(
                "MESSAGE_CREATE", eventId, memberId, channelId,
                msg.contentLength(), msg.authorIsBot(), msg.isReply(),
                msg.hasPoll(), msg.hasStickers(), msg.isTts(), msg.isPinned(),
                msg.hasAttachments(), msg.attachmentCount(), msg.reactionCount(),
                msg.mentionUserCount(), msg.mentionEveryone(), msg.embedCount(),
                msg.isVoiceMessage(), msg.type(),
                attachmentFilename, attachmentContentType,
                memberExtId, memberIsBoosting, memberJoinedAt,
                memberPCurrency, memberSCurrency,
                channelExtId, channelType, categoryExtId,
                null, null,
                null, null,
                null
        );
    }

    /**
     * Build a context for MEMBER_JOIN or MEMBER_LEAVE events.
     */
    public static RuleContext forMemberEvent(String eventType, long eventId, long memberId,
                                             Long memberExtId, boolean memberIsBoosting,
                                             String memberJoinedAt,
                                             int memberPCurrency, int memberSCurrency) {
        return new RuleContext(
                eventType, eventId, memberId,
                null,                                                     // channelId
                null, null, null, null, null, null, null,                  // message booleans
                null, null, null, null, null, null, null, null,            // message numerics + type
                null, null,                                                // attachment fields
                memberExtId, memberIsBoosting, memberJoinedAt,             // member state
                memberPCurrency, memberSCurrency,
                null, null, null,                                          // channel state
                null, null,                                                // role change
                null, null,                                                // voice
                null                                                       // createdAt
        );
    }

    /**
     * Build a context for MEMBER_ROLE_CHANGE events.
     */
    public static RuleContext forRoleChange(long eventId, long memberId,
                                            Long memberExtId, boolean memberIsBoosting,
                                            String memberJoinedAt,
                                            int memberPCurrency, int memberSCurrency,
                                            String rolesAdded, String rolesRemoved) {
        return new RuleContext(
                "MEMBER_ROLE_CHANGE", eventId, memberId,
                null,                                                     // channelId
                null, null, null, null, null, null, null,                  // message booleans
                null, null, null, null, null, null, null, null,            // message numerics + type
                null, null,                                                // attachment fields
                memberExtId, memberIsBoosting, memberJoinedAt,             // member state
                memberPCurrency, memberSCurrency,
                null, null, null,                                          // channel state
                rolesAdded, rolesRemoved,                                  // role change
                null, null,                                                // voice
                null                                                       // createdAt
        );
    }

    /**
     * Build a context for VOICE_JOIN, VOICE_LEAVE, or VOICE_MOVE events.
     */
    public static RuleContext forVoiceEvent(String eventType, long eventId, long memberId,
                                            Long channelId, Long memberExtId,
                                            boolean memberIsBoosting, String memberJoinedAt,
                                            int memberPCurrency, int memberSCurrency,
                                            Long channelExtId, String channelType,
                                            Double sessionDurationMinutes) {
        return new RuleContext(
                eventType, eventId, memberId,
                channelId,                                                 // channelId
                null, null, null, null, null, null, null,                  // message booleans
                null, null, null, null, null, null, null, null,            // message numerics + type
                null, null,                                                // attachment fields
                memberExtId, memberIsBoosting, memberJoinedAt,             // member state
                memberPCurrency, memberSCurrency,
                channelExtId, channelType, null,                           // channel state
                null, null,                                                // role change
                channelExtId, sessionDurationMinutes,                      // voice
                null                                                       // createdAt
        );
    }
}
