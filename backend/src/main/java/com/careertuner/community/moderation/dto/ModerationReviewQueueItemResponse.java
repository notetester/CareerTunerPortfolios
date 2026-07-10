package com.careertuner.community.moderation.dto;

import java.time.LocalDateTime;

public record ModerationReviewQueueItemResponse(
        Long postId,
        String title,
        String contentPreview,
        String authorName,
        String category,
        String aiCategory,
        double confidence,
        LocalDateTime createdAt,
        LocalDateTime moderatedAt
) {}
