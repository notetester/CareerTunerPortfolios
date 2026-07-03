package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        Long id,
        String type,
        String title,
        String description,
        String profileImageUrl,
        String displayName,
        boolean locked,
        int memberCount,
        boolean joined,
        boolean muted,
        String role,
        String joinPolicy,
        String invitePolicy,
        boolean anonymousAllowed,
        boolean anonymousOnly,
        boolean roomProfileRequired,
        UserBriefResponse peer,
        MessagePreviewResponse latestMessage,
        int unreadCount,
        LocalDateTime updatedAt
) {
}
