package com.careertuner.admin.legal.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 초안 저장 요청 (DRAFT 만 허용). clauses 가 null 이 아니면 기존 조항을 통째로 교체한다.
 */
public record SaveLegalDraftRequest(
        @Size(max = 20) String versionLabel,
        @Size(max = 500) String summary,
        Boolean isAdverse,
        LocalDateTime effectiveDate,
        @Valid List<ClauseInput> clauses
) {
    /** 입력 조항. seq 미지정 시 순서대로 1부터 재부여한다. */
    public record ClauseInput(
            @Min(1) Integer seq,
            @Size(max = 200) String title,
            String body
    ) {
    }
}
