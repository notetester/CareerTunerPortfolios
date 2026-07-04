package com.careertuner.admin.securityops.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SecurityReviewRequest(
        @NotBlank String reviewType,
        @NotBlank String subjectType,
        @NotBlank String subjectValue,
        @Min(0) @Max(100) Integer riskScore,
        String riskLevel,
        String status,
        String decisionAction,
        String reason,
        String evidenceJson,
        Long assignedTo) {
}
