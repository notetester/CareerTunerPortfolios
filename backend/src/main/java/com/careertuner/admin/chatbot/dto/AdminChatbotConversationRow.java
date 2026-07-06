package com.careertuner.admin.chatbot.dto;

import java.time.LocalDateTime;

/** 관리자 챗봇 대화 세션 1건(전역 목록). 메시지 본문은 목록에 싣지 않는다. */
public record AdminChatbotConversationRow(
        Long conversationId,
        Long userId,
        Integer messageCount,
        LocalDateTime updatedAt
) {}
