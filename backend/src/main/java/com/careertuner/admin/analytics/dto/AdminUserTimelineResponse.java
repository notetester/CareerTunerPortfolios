package com.careertuner.admin.analytics.dto;

import java.time.LocalDateTime;

import com.careertuner.admin.analytics.domain.AdminUserTimelineSource;

public record AdminUserTimelineResponse(
        String eventType,
        Long refId,
        String summary,
        String status,
        Integer score,
        LocalDateTime createdAt
) {
    public static AdminUserTimelineResponse from(AdminUserTimelineSource source) {
        return new AdminUserTimelineResponse(
                source.getEventType(), source.getRefId(), source.getSummary(), source.getStatus(),
                source.getScore(), source.getCreatedAt());
    }
}
