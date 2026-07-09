package com.careertuner.analysis.dto;

/**
 * 적합도 구간별 면접 평균 점수(상관 분석). 면접 세션이 있는 지원 건만 집계하며,
 * D 소유 면접 데이터는 읽기 전용 평균값으로만 사용한다.
 */
public record AnalysisFitInterviewBandResponse(
        String band,
        String label,
        int applicationCount,
        Integer averageFitScore,
        Integer averageInterviewScore
) {
}
