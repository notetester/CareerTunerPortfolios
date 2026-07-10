package com.careertuner.legal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공개 법적 문서 조회 응답.
 * 관리자 시행본이 없으면 코드에 포함된 기본 문서를 반환한다.
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
