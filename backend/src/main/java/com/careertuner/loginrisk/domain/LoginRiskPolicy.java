package com.careertuner.loginrisk.domain;

import java.time.LocalDateTime;

/**
 * 로그인 위험도(브루트포스) 잠금 정책 — 단일 행.
 * enabled=false 면 자동 잠금 미적용(무제약), true 면 maxFailedCount 회 실패 시 lockMinutes 분 잠금.
 */
public record LoginRiskPolicy(
        int id,
        boolean enabled,
        int maxFailedCount,
        int lockMinutes,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
