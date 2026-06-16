package com.careertuner.analysis.dto;

/**
 * 기술스택별 평균 적합도. 해당 기술이 등장한(매칭 또는 부족) 분석들의 평균 점수로,
 * 어떤 기술 중심 공고에서 강하고 약한지 보여준다.
 */
public record AnalysisSkillFitResponse(
        String skill,
        int analysisCount,
        int averageScore,
        boolean mostlyMatched
) {
}
