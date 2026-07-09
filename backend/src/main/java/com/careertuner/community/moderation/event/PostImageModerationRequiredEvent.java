package com.careertuner.community.moderation.event;

/**
 * 게시글 본문 이미지 검열 요청 이벤트. 글 저장/수정 트랜잭션 커밋 후
 * {@link PostImageModerationListener} 가 비동기로 받아 이미지 검열을 실행한다.
 */
public record PostImageModerationRequiredEvent(Long postId) {
}
