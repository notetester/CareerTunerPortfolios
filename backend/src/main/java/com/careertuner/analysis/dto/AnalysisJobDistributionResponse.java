package com.careertuner.analysis.dto;

/**
 * 자주 지원하는 직무 분포 — 기획 §8.9, 디자인 분석 §6.10(지원 직무·산업 분포).
 * averageFitScore는 분석 결과가 있는 건에 한해 계산하고, 없으면 null이다.
 */
public record AnalysisJobDistributionResponse(
        String jobTitle,
        int count,
        int percentage,
        Integer averageFitScore
) {
}
