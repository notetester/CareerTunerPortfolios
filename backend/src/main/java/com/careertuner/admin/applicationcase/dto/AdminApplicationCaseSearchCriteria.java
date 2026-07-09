package com.careertuner.admin.applicationcase.dto;

import java.time.LocalDate;

import lombok.Builder;

@Builder
public record AdminApplicationCaseSearchCriteria(
        String keyword,
        String status,
        boolean includeArchived,
        boolean includeDeleted,
        String sourceType,
        Boolean favorite,
        LocalDate createdFrom,
        LocalDate createdTo,
        LocalDate deadlineFrom,
        LocalDate deadlineTo,
        String analysisState,
        String sort,
        int limit,
        int offset) {
}
