package com.careertuner.community.moderation.event;

/**
 * "이 댓글을 검열해야 한다"는 이벤트.
 * PostModerationRequiredEvent 복제 — 대상만 commentId.
 */
public record CommentModerationRequiredEvent(Long commentId) {}
