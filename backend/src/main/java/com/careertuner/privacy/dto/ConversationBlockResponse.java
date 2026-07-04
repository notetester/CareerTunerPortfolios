package com.careertuner.privacy.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** 채팅방 차단 항목 — 파생 초대 차단 플래그 포함. */
public record ConversationBlockResponse(
        Long id,
        Long conversationId,
        String conversationTitle,
        String conversationType,
        Map<String, String> flags,
        LocalDateTime createdAt
) {}
