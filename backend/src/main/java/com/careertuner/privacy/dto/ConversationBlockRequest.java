package com.careertuner.privacy.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

/** 채팅방 차단 생성. flags 미지정 시 기본값(재초대만 차단). */
public record ConversationBlockRequest(
        @NotNull Long conversationId,
        Map<String, String> flags
) {}
