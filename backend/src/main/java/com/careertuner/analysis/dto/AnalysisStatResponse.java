package com.careertuner.analysis.dto;

public record AnalysisStatResponse(
        int totalApplications,
        int analyzedApplications,
        int averageFitScore,
        int highFitApplications,
        int readyApplications
) {
}
