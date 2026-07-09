package com.careertuner.admin.fitanalysis.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * gate review workflow 처리 요청(C 담당). reviewStatus: PENDING(되돌리기)/RESOLVED(검토 완료)/
 * REANALYSIS_REQUESTED(재분석 요청). note 가 있으면 GATE_REVIEW 운영 메모로 함께 남긴다.
 */
public record AdminGateReviewRequest(
        @NotBlank String reviewStatus,
        String note
) {
}
