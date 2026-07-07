package com.careertuner.admin.emailaudit.dto;

import java.time.LocalDateTime;

/**
 * 이메일 인증/재설정 토큰 발급 1건(전역 감사 뷰). 보안상 토큰 값은 노출하지 않는다.
 * status 는 SQL 에서 파생: USED / EXPIRED / PENDING.
 */
public record EmailAuditRow(
        Long id,
        Long userId,
        String email,
        String purpose,
        String status,
        boolean used,
        LocalDateTime expiredAt,
        LocalDateTime usedAt,
        LocalDateTime createdAt
) {}
