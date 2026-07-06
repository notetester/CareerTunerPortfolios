package com.careertuner.admin.correction.dto;

public record AdminCorrectionSummary(
        long totalRequests,
        long successCount,
        long failureCount,
        long memoCount,
        long todayCount
) {
}
