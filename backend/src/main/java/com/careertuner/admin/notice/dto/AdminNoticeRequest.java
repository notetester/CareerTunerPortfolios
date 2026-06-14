package com.careertuner.admin.notice.dto;

public record AdminNoticeRequest(
        String title,
        String content,
        String status,
        Boolean isPinned,
        String category,
        String thumbnailUrl
) {}
