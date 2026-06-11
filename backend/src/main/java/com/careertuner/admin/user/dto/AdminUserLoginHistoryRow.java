package com.careertuner.admin.user.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminUserLoginHistoryRow {
    private Long id;
    private Long userId;
    private String eventType;
    private String authProvider;
    private String loginMethod;
    private String loginIdentifier;
    private boolean success;
    private String failReason;
    private String ipAddress;
    private String userAgent;
    private String requestUri;
    private LocalDateTime createdAt;
}
