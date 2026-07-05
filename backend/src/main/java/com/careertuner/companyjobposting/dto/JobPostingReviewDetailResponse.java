package com.careertuner.companyjobposting.dto;

/**
 * 관리자 검토 상세 — 현재 게시본과 대기 중 변경본(diff 비교용).
 *
 * @param pendingRevision 수정 검토 대기 변경본(신규 등록 검토면 null). 프런트가 posting 과 필드별 비교한다.
 */
public record JobPostingReviewDetailResponse(
        CompanyJobPostingResponse posting,
        Long pendingRevisionId,
        JobPostingUpsertRequest pendingRevision
) {
}
