package com.careertuner.dashboard.dto;

public record DashboardSkillGapResponse(
        String skill,
        int count,
        int total,
        int percentage
) {
}
