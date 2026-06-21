package com.careertuner.admin.legal.dto;

import java.time.LocalDateTime;

/**
 * 관리자 버전 목록 항목.
 * badge = live | next | old | draft (effective_date vs NOW() 로 계산).
 */
public record AdminLegalVersionResponse(
        Long id,
        String docType,
        String versionLabel,
        String status,
        String badge,
        String summary,
        boolean isAdverse,
        LocalDateTime effectiveDate,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int clauseCount
) {
}
