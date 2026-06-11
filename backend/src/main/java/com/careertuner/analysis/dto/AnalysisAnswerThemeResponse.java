package com.careertuner.analysis.dto;

/**
 * 자주 개선이 필요한 답변 요소 — 기획 §8.9(답변의 공통 약점), 디자인 분석 §6.10.
 * 질문 유형별 평균 점수와 가장 점수가 낮은 답변의 피드백을 함께 제공한다.
 */
public record AnalysisAnswerThemeResponse(
        String questionType,
        int answerCount,
        int averageScore,
        String sampleFeedback
) {
}
