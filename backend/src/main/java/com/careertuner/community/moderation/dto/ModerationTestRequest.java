package com.careertuner.community.moderation.dto;

import jakarta.validation.constraints.NotBlank;

public record ModerationTestRequest(
        String title,
        @NotBlank String content
) {}
