package com.careertuner.analysis.dto;

public record AnalysisWeeklyChangeResponse(
        Integer fitScoreDelta,
        Integer gapCountDelta,
        Integer interviewScoreDelta,
        String summary
) {
}
