package com.careertuner.admin.ticket.dto;

/**
 * 상담사 AI 어시스트 — 회원 요약 응답.
 * 회원 정보·과거 문의 이력을 LLM 으로 요약한 텍스트를 즉시 반환만 한다(저장 안 함).
 */
public record AdminTicketSummaryResponse(
        String summary
) {}
