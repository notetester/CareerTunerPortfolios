package com.careertuner.analysis.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LearningPlanRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank @Size(max = 255) String targetSkill,
        LocalDate startDate,
        LocalDate endDate
) {
}
