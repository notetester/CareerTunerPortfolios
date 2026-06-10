package com.careertuner.analysis.dto;

/**
 * 반복 강점(자주 활용되는 강점 경험) — 기획 §8.9, 디자인 분석 §6.10.
 * 적합도 분석의 matched_skills를 지원 건 단위로 집계한다.
 */
public record AnalysisStrengthTrendResponse(
        String skill,
        int count,
        int total,
        int percentage
) {
}
