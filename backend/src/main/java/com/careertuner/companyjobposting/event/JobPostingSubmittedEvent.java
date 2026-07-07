package com.careertuner.companyjobposting.event;

/**
 * 공고 검토 제출 이벤트 — 커밋 후 관리자 팬아웃(NEW_JOB_POSTING_REVIEW).
 *
 * @param revisionId 수정 검토면 변경본 id, 신규 등록 검토면 null
 */
public record JobPostingSubmittedEvent(Long postingId, Long revisionId, String title) {
}
