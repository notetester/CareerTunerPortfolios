package com.careertuner.applicationcase.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmJobPostingExtractionRequest(
        @NotBlank String extractedText
) {
}
