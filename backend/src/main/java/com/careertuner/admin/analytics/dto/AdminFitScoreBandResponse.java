package com.careertuner.admin.analytics.dto;

public record AdminFitScoreBandResponse(
        String label,
        int count,
        int percentage
) {
}
