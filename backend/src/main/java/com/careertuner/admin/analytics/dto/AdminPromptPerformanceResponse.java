package com.careertuner.admin.analytics.dto;

import com.careertuner.admin.analytics.domain.AdminPromptPerformanceSource;

public record AdminPromptPerformanceResponse(
        String promptKey,
        String promptVersion,
        int totalCount,
        int successCount,
        int fallbackCount,
        int failedCount,
        int successRate,
        int averageTokenUsage
) {
    public static AdminPromptPerformanceResponse from(AdminPromptPerformanceSource source) {
        int rate = source.getTotalCount() == 0 ? 0
                : (int) Math.round(source.getSuccessCount() * 100.0 / source.getTotalCount());
        return new AdminPromptPerformanceResponse(
                source.getPromptKey(), source.getPromptVersion(), source.getTotalCount(), source.getSuccessCount(),
                source.getFallbackCount(), source.getFailedCount(), rate, source.getAverageTokenUsage());
    }
}
