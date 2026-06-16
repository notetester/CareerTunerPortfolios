package com.careertuner.correction.dto;

import jakarta.validation.constraints.Size;

public record CorrectionCreateRequest(
        String correctionType,
        Long applicationCaseId,
        String originalText,
        String sourceType,
        Long sourceRefId,
        @Size(max = 1000) String questionText
) {
}
