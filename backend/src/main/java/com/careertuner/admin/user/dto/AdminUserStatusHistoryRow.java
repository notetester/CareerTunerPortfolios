package com.careertuner.admin.user.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminUserStatusHistoryRow {
    private Long id;
    private Long userId;
    private Long actorUserId;
    private String previousStatus;
    private String newStatus;
    private String reason;
    private String memo;
    private LocalDateTime blockedUntil;
    private LocalDateTime createdAt;
}
