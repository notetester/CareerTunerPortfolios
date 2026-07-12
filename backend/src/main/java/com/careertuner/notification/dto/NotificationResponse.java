package com.careertuner.notification.dto;

import java.time.LocalDateTime;

import com.careertuner.notification.domain.NotificationDestinationPlatform;

public record NotificationResponse(
        Long id,
        String type,
        String targetType,
        Long targetId,
        String senderRelation,
        NotificationDestinationPlatform destinationPlatform,
        String title,
        String message,
        String link,
        boolean read,
        LocalDateTime createdAt,
        ActorDto actor
) {
    public record ActorDto(
            /** 탈퇴한 발신자는 공개 프로필 링크를 만들지 않도록 null. */
            Long id,
            String name,
            String avatarUrl
    ) {}
}
