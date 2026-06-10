package com.careertuner.support.dto;

public record NoticeListResponse(
        Long id,
        String title,
        String tag,
        boolean isPinned,
        int viewCount,
        String createdAt
) {}
