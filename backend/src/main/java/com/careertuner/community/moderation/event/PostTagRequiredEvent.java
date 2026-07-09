package com.careertuner.community.moderation.event;

/**
 * "이 게시글에 AI 태깅이 필요하다"는 이벤트.
 * 리스너가 tag()를 호출한다.
 */
public record PostTagRequiredEvent(Long postId) {}
