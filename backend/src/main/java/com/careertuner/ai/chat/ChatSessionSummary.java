package com.careertuner.ai.chat;

/**
 * 세션 목록(사이드바) 항목 — 인테이크(지원건) 세션 1건 요약.
 * application_case_id 가 있는(지원 건이 확정된) 대화만 목록에 오른다.
 */
public record ChatSessionSummary(Long conversationId, String title) {}
