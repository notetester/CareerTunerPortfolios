package com.careertuner.applicationcase.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewJobPostingExtractionRequest(
        @NotBlank String extractedText
) {
}
