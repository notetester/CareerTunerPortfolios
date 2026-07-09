package com.careertuner.fitanalysis.ai;

/**
 * 요구조건-스펙 비교 매트릭스 한 행.
 *
 * <p>공고 요구조건(필수/우대)별로 지원자 보유 여부 판정과 근거를 담는다.
 * conditionType: REQUIRED/PREFERRED, matchStatus: MET/PARTIAL/UNMET.
 */
public record FitConditionMatch(
        String condition,
        String conditionType,
        String matchStatus,
        String evidence
) {
}
