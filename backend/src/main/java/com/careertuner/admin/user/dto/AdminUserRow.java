package com.careertuner.admin.user.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminUserRow {
    private Long id;
    private String email;
    private String name;
    private boolean passwordEnabled;
    private boolean emailVerified;
    private String userType;
    private String role;
    private String status;
    private String plan;
    private int credit;
    private LocalDateTime lastLoginAt;
    private LocalDateTime dormantAt;
    private String blockedReason;
    private LocalDateTime blockedUntil;
    private LocalDateTime deletedAt;
    private LocalDateTime statusChangedAt;
    private Long statusChangedBy;
    private int failedLoginCount;
    private LocalDateTime lastFailedLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long loginSuccessCount;
    private long loginFailCount;
}
