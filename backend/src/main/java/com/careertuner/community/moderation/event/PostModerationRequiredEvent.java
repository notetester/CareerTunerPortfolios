package com.careertuner.community.moderation.event;

/**
 * "이 게시글을 검열해야 한다"는 이벤트.
 * 생성/수정 구분 없이 동일 — 리스너 동작이 같다.
 */
public record PostModerationRequiredEvent(Long postId) {}
