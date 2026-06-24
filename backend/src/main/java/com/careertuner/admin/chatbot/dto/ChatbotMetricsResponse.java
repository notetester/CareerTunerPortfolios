package com.careertuner.admin.chatbot.dto;

/**
 * AI 상담 운영 콘솔 메트릭 밴드 응답(4카드).
 * <p>아직 데이터 소스(턴 응답 로그 집계)가 없는 카드는 null → 프론트는 "수집 중"/"—"로 표시한다.
 * <b>S1 범위</b>: {@code faqGap}(FAQ 공백)만 채우고 나머지 3카드는 null.
 *
 * @param autoResolveRate    자동 해결률(0~1) — 후속 단계
 * @param faqReferenceCount  FAQ 참조 응답 수(건) — 후속 단계
 * @param faqGap             FAQ 공백(미해결 질문 군집 수) — S1
 * @param handoffRate        상담사 전환율(0~1) — 후속 단계
 */
public record ChatbotMetricsResponse(
        ChatbotMetricCard autoResolveRate,
        ChatbotMetricCard faqReferenceCount,
        ChatbotMetricCard faqGap,
        ChatbotMetricCard handoffRate
) {
}
