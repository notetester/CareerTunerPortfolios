package com.careertuner.community.moderation.dto;

public record ModerationTestResponse(
        boolean toxic,
        String category,
        double confidence,
        long elapsedMs
) {}
