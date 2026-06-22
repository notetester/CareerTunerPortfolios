package com.careertuner.admin.legal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 초안 저장 요청 (DRAFT 만 허용). clauses 가 null 이 아니면 기존 조항을 통째로 교체한다.
 */
public record SaveLegalDraftRequest(
        String versionLabel,
        String summary,
        Boolean isAdverse,
        LocalDateTime effectiveDate,
        List<ClauseInput> clauses
) {
    /** 입력 조항. seq 미지정 시 순서대로 1부터 재부여한다. */
    public record ClauseInput(Integer seq, String title, String body) {
    }
}
