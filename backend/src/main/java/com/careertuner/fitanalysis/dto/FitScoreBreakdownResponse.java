package com.careertuner.fitanalysis.dto;

public record FitScoreBreakdownResponse(
        String key,
        String label,
        int earned,
        int maximum,
        String explanation
) {
}
