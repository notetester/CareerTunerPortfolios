package com.careertuner.admin.analytics.dto;

public record AdminSkillGapResponse(
        String skill,
        int count,
        int total,
        int percentage
) {
}
