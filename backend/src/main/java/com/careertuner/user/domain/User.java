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
    private String loginId;           // 로그인 아이디(문자열, 선택 설정·전역 UNIQUE·설정 후 변경 불가)
    private String phone;             // 전화번호(선택, 전역 UNIQUE)
    private boolean phoneVerified;    // 전화번호 인증 여부(인증은 선택적·스텁)
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
