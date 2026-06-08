package com.careertuner.fitanalysis.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.careertuner.fitanalysis.domain.FitAnalysisResult;

public record FitAnalysisApplicationResponse(
        Long id,
        String companyName,
        String jobTitle,
        LocalDate postingDate,
        String status,
        boolean favorite,
        LocalDateTime updatedAt
) {
    public static FitAnalysisApplicationResponse from(FitAnalysisResult result) {
        return new FitAnalysisApplicationResponse(
                result.getApplicationCaseId(),
                result.getCompanyName(),
                result.getJobTitle(),
                result.getPostingDate(),
                result.getApplicationStatus(),
                result.isFavorite(),
                result.getApplicationUpdatedAt());
    }
}
