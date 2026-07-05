package com.careertuner.admin.audit.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 로그인 감사 그리드 행 — user_login_history + users(email/name) 조인 결과. */
@Data
public class AdminLoginAuditRow {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
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
