package com.careertuner.admin.chatbot.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 군집화 입력용 미응답 질문 행(내부 — 매퍼 → 군집화 서비스). 응답 DTO 아님.
 */
@Data
public class UnansweredRow {
    private Long id;
    private String question;
    private String questionNorm;
    private Double topSimilarity;
    private Long bestFaqId;
    /** 질문 임베딩 JSON 배열(수집 시 저장). 없으면 null → 정확매칭으로 폴백. */
    private String embedding;
    private LocalDateTime createdAt;
}
