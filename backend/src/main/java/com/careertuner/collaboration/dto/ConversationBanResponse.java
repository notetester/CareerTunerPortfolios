package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

/** ban 목록 항목. */
public record ConversationBanResponse(
        Long userId,
        String displayName,
        String reason,
        Long bannedBy,
        LocalDateTime bannedAt
) {
}
