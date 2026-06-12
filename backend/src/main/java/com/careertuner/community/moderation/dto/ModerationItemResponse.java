package com.careertuner.community.moderation.dto;

import java.time.LocalDateTime;

/**
 * 관리자 검열 목록 항목 응답.
 */
public record ModerationItemResponse(
        Long postId,
        String title,
        String authorName,
        String category,
        String status,
        boolean toxic,
        String aiCategory,
        double confidence,
        int attemptCount,
        LocalDateTime createdAt,
        LocalDateTime moderatedAt
) {}
