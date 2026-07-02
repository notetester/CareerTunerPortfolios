package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

public record MessagePreviewResponse(
        Long id,
        String kind,
        String content,
        LocalDateTime createdAt
) {
}
