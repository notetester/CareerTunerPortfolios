package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        Long id,
        String type,
        String title,
        String description,
        String displayName,
        boolean locked,
        int memberCount,
        boolean joined,
        UserBriefResponse peer,
        MessagePreviewResponse latestMessage,
        int unreadCount,
        LocalDateTime updatedAt
) {
}
