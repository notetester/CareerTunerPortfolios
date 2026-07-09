package com.careertuner.admin.companyanalysis.dto;

import java.time.LocalDate;

import lombok.Builder;

@Builder
public record AdminCompanyAnalysisSearchCriteria(
        String keyword,
        String sourceType,
        String industry,
        Boolean confirmed,
        Boolean hasMemo,
        Boolean checked,
        Boolean refreshDue,
        Long applicationCaseId,
        Long userId,
        LocalDate createdFrom,
        LocalDate createdTo,
        String sort,
        int limit,
        int offset) {
}
