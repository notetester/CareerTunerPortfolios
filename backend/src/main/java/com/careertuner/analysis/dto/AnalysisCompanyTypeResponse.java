package com.careertuner.analysis.dto;

public record AnalysisCompanyTypeResponse(
        String companyType,
        int applicationCount,
        Integer averageFitScore
) {
}
