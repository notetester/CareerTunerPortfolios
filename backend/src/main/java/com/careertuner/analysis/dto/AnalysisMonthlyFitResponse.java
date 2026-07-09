package com.careertuner.analysis.dto;

/**
 * 월별 평균 적합도 변화. month 는 ISO yyyy-MM 형식이며 화면에서 라벨로 변환한다.
 */
public record AnalysisMonthlyFitResponse(
        String month,
        int averageScore,
        int analysisCount
) {
}
