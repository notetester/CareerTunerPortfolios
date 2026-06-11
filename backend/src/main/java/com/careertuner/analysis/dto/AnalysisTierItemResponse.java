package com.careertuner.analysis.dto;

/** 지원 분류(상향/적정/안전)에 속한 지원 건 요약. */
public record AnalysisTierItemResponse(
        Long applicationCaseId,
        String companyName,
        String jobTitle,
        Integer fitScore
) {
}
