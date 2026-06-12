package com.careertuner.analysis.dto;

import java.util.List;

/**
 * 상향/적정/안전 지원 분류. 최신 적합도 점수 기준의 결정적 분류로,
 * SAFE(80점 이상)/MATCH(60~79점)/CHALLENGE(60점 미만) 세 구간을 항상 반환한다.
 */
public record AnalysisApplicationTierResponse(
        String tier,
        String label,
        String description,
        List<AnalysisTierItemResponse> items
) {
}
