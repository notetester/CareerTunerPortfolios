package com.careertuner.admin.jobanalysis.dto;

import java.time.LocalDate;

import lombok.Builder;

@Builder
public record AdminJobAnalysisSearchCriteria(
        String keyword,
        String difficulty,
        Boolean confirmed,
        Boolean hasMemo,
        Long applicationCaseId,
        Long userId,
        LocalDate createdFrom,
        LocalDate createdTo,
        String sort,
        int limit,
        int offset) {
}
