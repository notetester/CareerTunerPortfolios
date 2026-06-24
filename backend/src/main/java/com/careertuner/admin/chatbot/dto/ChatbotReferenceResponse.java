package com.careertuner.admin.chatbot.dto;

import java.time.LocalDateTime;

/**
 * 참조 대화 표(F3-A) 한 행 — 디자인의 av-table 컬럼과 1:1.
 *
 * @param createdAt   응답 시각
 * @param question    사용자 질문(verbatim)
 * @param faqQuestion 인용한 FAQ 질문(없으면 null)
 * @param similarity  FAQ 최고 유사도(없으면 null) — 0.85 이상 green/미만 amber 는 프론트 표시
 * @param result      결과: "상담 전환"(handoff) 또는 "해결"
 */
public record ChatbotReferenceResponse(
        LocalDateTime createdAt,
        String question,
        String faqQuestion,
        Double similarity,
        String result
) {
}
