package com.careertuner.user.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** users 테이블 VO. 일반/소셜 로그인 공용. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;
    private String email;
    private String password;          // BCrypt 해시. 소셜 전용 계정은 null
    private boolean passwordEnabled;  // 비밀번호 로그인 가능 여부
    private String name;
    private boolean emailVerified;
    private String userType;          // JOB_SEEKER/CAREER_CHANGER/EXPERIENCED
    private String role;              // USER/ADMIN
    private String status;            // ACTIVE/DORMANT/BLOCKED/DELETED
    private String plan;              // FREE/BASIC/PRO/PREMIUM
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
}
