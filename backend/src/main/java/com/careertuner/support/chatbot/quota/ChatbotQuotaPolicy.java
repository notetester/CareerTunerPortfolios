package com.careertuner.support.chatbot.quota;

import java.time.LocalDateTime;

/**
 * AI 챗봇 일일 사용 쿼터 정책 — 단일 행.
 * enabled=false 면 무제약, true 면 로그인 사용자 1인당 하루 dailyLimit 질문까지.
 */
public record ChatbotQuotaPolicy(
        int id,
        boolean enabled,
        int dailyLimit,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
