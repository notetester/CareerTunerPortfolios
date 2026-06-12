package com.careertuner.analysis.dto;

public record AnalysisCorrectionCorrelationResponse(
        int correctedApplications,
        int uncorrectedApplications,
        Integer correctedAverageFitScore,
        Integer uncorrectedAverageFitScore,
        Integer scoreDelta
) {
}
