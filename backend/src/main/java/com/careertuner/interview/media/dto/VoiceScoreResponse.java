package com.careertuner.interview.media.dto;

import tools.jackson.databind.JsonNode;

/**
 * 자체 추론 서버 음성 점수 결과 (ADR-006).
 * score: 0~100 종합. detail: 항목별(pace/fluency/stability/confidence/responsiveness).
 * metrics: 추출 피처. source: {@code rule}(규칙 폴백) | {@code lightgbm}(학습 모델).
 */
public record VoiceScoreResponse(
        int score,
        JsonNode detail,
        JsonNode metrics,
        String source) {
}
