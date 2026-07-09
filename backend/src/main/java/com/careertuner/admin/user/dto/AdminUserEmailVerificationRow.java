package com.careertuner.admin.user.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminUserEmailVerificationRow {
    private Long id;
    private Long userId;
    private String email;
    private String purpose;
    private LocalDateTime expiredAt;
    private boolean used;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
