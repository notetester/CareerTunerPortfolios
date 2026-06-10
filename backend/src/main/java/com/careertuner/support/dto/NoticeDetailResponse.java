package com.careertuner.support.dto;

public record NoticeDetailResponse(
        Long id,
        String title,
        String content,
        String tag,
        boolean isPinned,
        int viewCount,
        String createdAt
) {}
