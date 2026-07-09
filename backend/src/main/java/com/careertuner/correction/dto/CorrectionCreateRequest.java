package com.careertuner.correction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CorrectionCreateRequest(
        String correctionType,
        Long applicationCaseId,
        String originalText,
        String sourceType,
        Long sourceRefId,
        @Size(max = 1000) String questionText,
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "[A-Za-z0-9:_-]+")
        String policyAcknowledgementKey,
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "[A-Za-z0-9:_-]+")
        String requestKey
) {
}
