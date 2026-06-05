package com.careertuner.applicationcase.dto;

import jakarta.validation.constraints.Size;

public record JobPostingRequest(
        String originalText,
        @Size(max = 512) String uploadedFileUrl,
        String extractedText,
        @Size(max = 20) String sourceType
) {
}
