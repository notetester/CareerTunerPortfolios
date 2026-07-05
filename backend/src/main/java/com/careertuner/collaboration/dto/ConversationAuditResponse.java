package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

/** 방 활동 로그 항목 응답. */
public record ConversationAuditResponse(
        Long id,
        Long actorId,
        String actorName,
        Long targetUserId,
        String targetName,
        String action,
        String detail,
        LocalDateTime createdAt
) {
}
