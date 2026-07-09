package com.careertuner.community.event;

/**
 * "커뮤니티 글이 PUBLISHED 상태로 새로 생성됐다"는 이벤트.
 * <p>글 저장 트랜잭션 커밋 후(AFTER_COMMIT) {@code RecommendedPostNotifyListener}가 소비해
 * 관심 분야가 일치하는 사용자에게 추천 알림(RECOMMENDED_POST)을 발행한다.
 * 수정(update) 시에는 발행하지 않는다 — 같은 글로 추천 알림이 반복되는 것을 막는다.
 */
public record PostPublishedEvent(Long postId) {}
