package com.careertuner.applicationcase.dto;

import java.time.LocalDateTime;

public record AiUsageFailureResponse(
        String featureType,
        String errorMessage,
        LocalDateTime createdAt
) {
}
