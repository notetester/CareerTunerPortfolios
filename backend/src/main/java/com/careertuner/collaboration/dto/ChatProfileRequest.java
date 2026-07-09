package com.careertuner.collaboration.dto;

public record ChatProfileRequest(
        String nickname,
        String avatarUrl,
        String description,
        Boolean defaultProfile) {
}
