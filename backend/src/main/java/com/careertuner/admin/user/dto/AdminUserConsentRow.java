package com.careertuner.admin.user.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminUserConsentRow {
    private Long id;
    private Long userId;
    private String consentType;
    private boolean agreed;
    private LocalDateTime agreedAt;
    private LocalDateTime revokedAt;
    private String source;
    private LocalDateTime createdAt;
}
