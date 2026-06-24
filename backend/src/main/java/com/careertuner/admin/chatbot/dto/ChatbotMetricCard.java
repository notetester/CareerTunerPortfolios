package com.careertuner.admin.chatbot.dto;

import java.util.List;

/**
 * 메트릭 밴드 카드 1개.
 * <p>value/deltaVsPrev 의 단위는 카드 의미를 따른다: 비율 카드 = 0~1 분수, 카운트 카드 = 건수.
 * 데이터 소스가 아직 없는 카드는 응답에서 통째로 null(프론트가 "수집 중"/"—"로 표시 — 가짜 숫자 금지).
 *
 * @param value       기간 대표값(비율 0~1 또는 건수)
 * @param deltaVsPrev 직전 동일길이 기간 대비 증감(같은 단위; 상승/하락 표시용)
 * @param series      일자별 시계열(스파크라인; 빈 날짜는 0 으로 채워 연속)
 */
public record ChatbotMetricCard(Double value, Double deltaVsPrev, List<ChatbotMetricPoint> series) {
}
