package com.careertuner.analysis.dto;

public record JobReadinessResponse(
        String jobTitle,
        int readiness,
        int applicationCount,
        String trend
) {
}
