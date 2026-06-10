package com.careertuner.analysis.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.careertuner.analysis.domain.AnalysisSource;

public record AnalysisApplicationSummaryResponse(
        Long applicationCaseId,
        String companyName,
        String jobTitle,
        LocalDate postingDate,
        String status,
        boolean favorite,
        Integer fitScore,
        LocalDateTime analyzedAt
) {
    public static AnalysisApplicationSummaryResponse from(AnalysisSource source) {
        return new AnalysisApplicationSummaryResponse(
                source.getApplicationCaseId(),
                source.getCompanyName(),
                source.getJobTitle(),
                source.getPostingDate(),
                source.getStatus(),
                source.isFavorite(),
                source.getFitScore(),
                source.getAnalyzedAt());
    }
}
