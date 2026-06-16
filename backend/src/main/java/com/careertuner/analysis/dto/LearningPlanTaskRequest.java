package com.careertuner.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LearningPlanTaskRequest(
        @NotBlank @Size(max = 1000) String task,
        Integer sortOrder
) {
}
