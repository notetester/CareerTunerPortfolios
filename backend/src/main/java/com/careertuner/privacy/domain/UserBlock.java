package com.careertuner.privacy.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 개인 계정 차단 1건. flagsJson 의 non-null 표면 값이 관계 정책보다 우선한다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBlock {

    private Long id;
    private Long userId;
    private Long blockedUserId;
    private String flagsJson;
    private boolean blockIp;
    private String memo;
    /** 익명 콘텐츠 기반 차단의 표시 라벨 — non-null 이면 응답에서 이름/이메일을 이 라벨로 마스킹한다. */
    private String maskedLabel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // JOIN 표시용
    private String blockedUserName;
    private String blockedUserEmail;
    private String blockedUserStatus;
}
