package com.careertuner.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInterviewSessionRequest(
        @NotNull Long applicationCaseId,
        @NotBlank String mode) {
}
