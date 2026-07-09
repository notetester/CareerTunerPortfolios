package com.careertuner.admin.ops.dto;

public record AdminActionLogCreate(
        Long actorUserId,
        Long targetUserId,
        String actionType,
        String targetType,
        String beforeValue,
        String afterValue,
        String reason,
        String ipAddress,
        String userAgent) {
}
