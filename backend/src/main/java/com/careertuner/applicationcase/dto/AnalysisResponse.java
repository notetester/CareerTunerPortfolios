package com.careertuner.applicationcase.dto;

import com.careertuner.jobanalysis.dto.JobAnalysisResponse;

public record AnalysisResponse(
        ApplicationCaseResponse applicationCase,
        JobAnalysisResponse jobAnalysis,
        FitAnalysisResponse fitAnalysis
) {
}
