package com.careertuner.analysis.dto;

/** 여러 지원 건에서 결정적으로 감지한 취업 준비 리스크와 다음 행동. */
public record AnalysisCareerRiskResponse(
        String riskType,
        String severity,
        String title,
        String detail,
        String action
) {
}
