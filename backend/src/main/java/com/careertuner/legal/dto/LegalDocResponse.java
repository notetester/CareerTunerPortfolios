package com.careertuner.legal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공개 법적 문서 조회 응답.
 * 시행본이 없으면 sections 는 빈 리스트로 내려가며(404 아님), 나머지 메타는 null.
 */
public record LegalDocResponse(
        String docType,
        String title,
        String versionLabel,
        LocalDateTime effectiveDate,
        LocalDateTime updatedAt,
        String summary,
        List<ClauseDto> sections
) {
    /** 평면 조항 (제목 + 본문). */
    public record ClauseDto(int seq, String title, String body) {
    }
}
