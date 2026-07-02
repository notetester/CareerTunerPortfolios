package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        Long id,
        UserBriefResponse peer,
        MessagePreviewResponse latestMessage,
        int unreadCount,
        LocalDateTime updatedAt
) {
}
