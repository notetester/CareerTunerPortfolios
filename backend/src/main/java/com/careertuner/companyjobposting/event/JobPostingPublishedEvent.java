package com.careertuner.companyjobposting.event;

/** 공고 게시 확정 이벤트 — 커밋 후 RECOMMENDED_JOB 자동 발행 트리거. */
public record JobPostingPublishedEvent(Long postingId) {
}
