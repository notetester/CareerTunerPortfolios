package com.careertuner.admin.ops.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminActionLogRow {
    private Long id;
    private Long actorUserId;
    private String actorEmail;
    private Long targetUserId;
    private String targetEmail;
    private String actionType;
    private String targetType;
    private String beforeValue;
    private String afterValue;
    private String reason;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
