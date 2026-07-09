package com.careertuner.admin.notice.dto;

public record AdminNoticeRequest(
        String title,
        String content,
        String status,
        Boolean isPinned,
        String category,
        String thumbnailUrl,
        // 예약 발행 시각(ISO LocalDateTime, 예: "2026-07-01T09:00"). status=SCHEDULED 일 때만 사용.
        String scheduledAt
) {}
