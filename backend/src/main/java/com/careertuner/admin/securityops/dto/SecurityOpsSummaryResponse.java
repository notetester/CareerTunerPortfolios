package com.careertuner.admin.securityops.dto;

public record SecurityOpsSummaryResponse(
        long activeBlockRules,
        long pendingWafEvents,
        long openReviews,
        long openAppeals,
        long enabledProviders) {
}
