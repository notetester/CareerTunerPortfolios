package com.careertuner.dashboard.dto;

import java.time.LocalDateTime;

import com.careertuner.dashboard.domain.DashboardNotificationSource;

public record DashboardNotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        String link,
        boolean read,
        LocalDateTime createdAt
) {

    public static DashboardNotificationResponse from(DashboardNotificationSource source) {
        return new DashboardNotificationResponse(
                source.getId(),
                source.getType(),
                source.getTitle(),
                source.getMessage(),
                source.getLink(),
                source.isRead(),
                source.getCreatedAt());
    }
}
