package com.careertuner.admin.credit.dto;

public record AdminCreditSearchCriteria(
        String keyword,
        Long userId,
        String type,
        int page,
        int size,
        long offset
) {
}
