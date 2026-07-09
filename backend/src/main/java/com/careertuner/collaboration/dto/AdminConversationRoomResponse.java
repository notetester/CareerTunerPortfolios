package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

/** 관리자 방 오버사이트 목록 행. */
public record AdminConversationRoomResponse(
        Long id,
        String type,
        String title,
        String description,
        boolean locked,
        int memberCount,
        LocalDateTime updatedAt
) {
}
