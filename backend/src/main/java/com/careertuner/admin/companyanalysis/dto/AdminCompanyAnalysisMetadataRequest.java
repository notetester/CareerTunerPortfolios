package com.careertuner.admin.companyanalysis.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Size;

public record AdminCompanyAnalysisMetadataRequest(
        @Size(max = 30) String sourceType,
        LocalDateTime checkedAt,
        LocalDateTime refreshRecommendedAt
) {
}
