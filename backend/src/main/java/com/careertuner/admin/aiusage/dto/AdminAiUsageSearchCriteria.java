package com.careertuner.admin.aiusage.dto;

import java.time.LocalDate;

import lombok.Builder;

@Builder
public record AdminAiUsageSearchCriteria(
        String featureType,
        String status,
        String keyword,
        Long applicationCaseId,
        Long userId,
        String model,
        LocalDate createdFrom,
        LocalDate createdTo,
        String sort,
        int limit,
        int offset) {
}
