package com.careertuner.admin.correction.dto;

public record AdminCorrectionSearchCriteria(
        String keyword,
        String correctionType,
        String status,
        String memoState,
        int page,
        int size,
        long offset
) {
}
