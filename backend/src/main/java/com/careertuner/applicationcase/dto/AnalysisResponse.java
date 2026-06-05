package com.careertuner.applicationcase.dto;

public record AnalysisResponse(
        ApplicationCaseResponse applicationCase,
        JobAnalysisResponse jobAnalysis,
        FitAnalysisResponse fitAnalysis
) {
}
