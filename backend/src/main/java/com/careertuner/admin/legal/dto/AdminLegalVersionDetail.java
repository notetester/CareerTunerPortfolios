package com.careertuner.admin.legal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 버전 상세 + 조항.
 */
public record AdminLegalVersionDetail(
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
        List<ClauseDto> clauses
) {
    public record ClauseDto(Long id, int seq, String title, String body) {
    }
}
