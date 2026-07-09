package com.careertuner.support.chatbot.quota;

/** 챗봇 쿼터 정책 편집 요청(부분 갱신 — null 이면 기존값 유지). */
public record ChatbotQuotaPolicyRequest(
        Boolean enabled,
        Integer dailyLimit,
        String reason
) {}
