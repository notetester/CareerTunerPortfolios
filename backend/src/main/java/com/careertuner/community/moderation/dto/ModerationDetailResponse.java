package com.careertuner.community.moderation.dto;

import java.time.LocalDateTime;

/**
 * 관리자 검열 단건 상세 응답.
 */
public record ModerationDetailResponse(
        Long postId,
        String title,
        String content,
        String authorName,
        String category,
        String status,
        boolean toxic,
        String aiCategory,
        double confidence,
        String model,
        int attemptCount,
        LocalDateTime createdAt,
        LocalDateTime moderatedAt
) {}
