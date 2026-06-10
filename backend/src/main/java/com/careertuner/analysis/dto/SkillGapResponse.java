package com.careertuner.analysis.dto;

public record SkillGapResponse(
        String skill,
        int count,
        int total,
        int percentage
) {
}
