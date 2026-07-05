package com.careertuner.companyjobposting.event;

/** 공고 검토 확정 이벤트 — 커밋 후 기업에 JOB_POSTING_REVIEW_RESULT 알림. */
public record JobPostingReviewedEvent(Long postingId,
                                      Long companyUserId,
                                      String title,
                                      boolean approved,
                                      boolean revisionReview,
                                      String rejectReason) {
}
