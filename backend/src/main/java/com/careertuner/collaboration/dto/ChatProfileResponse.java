package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

public record ChatProfileResponse(
        Long id,
        String nickname,
        String avatarUrl,
        String description,
        boolean defaultProfile,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
