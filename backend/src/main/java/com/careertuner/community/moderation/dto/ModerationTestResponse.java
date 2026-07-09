package com.careertuner.community.moderation.dto;

public record ModerationTestResponse(
        boolean toxic,
        String category,
        /** null = 판정 불성립(mock 폴백/confidence 누락) — 0.0 확신과 구분 */
        Double confidence,
        long elapsedMs
) {}
