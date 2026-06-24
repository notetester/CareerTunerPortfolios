package com.careertuner.admin.user.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminUserRefreshTokenRow {
    private Long id;
    private Long userId;
    private LocalDateTime expiredAt;
    private boolean revoked;
    private LocalDateTime revokedAt;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
