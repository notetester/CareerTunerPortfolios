package com.careertuner.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String targetType,
        Long targetId,
        String title,
        String message,
        String link,
        boolean read,
        LocalDateTime createdAt,
        ActorDto actor
) {
    public record ActorDto(
            Long id,
            String name,
            String avatarUrl
    ) {}
}
