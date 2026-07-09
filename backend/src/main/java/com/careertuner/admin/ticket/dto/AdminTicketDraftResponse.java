package com.careertuner.admin.ticket.dto;

/**
 * 상담사 AI 어시스트 — 티켓 답변 초안 응답.
 * 초안은 DB에 저장하지 않고 즉시 반환만 한다(상담사가 검토·수정 후 별도 reply로 발송).
 */
public record AdminTicketDraftResponse(
        String draft
) {}
