package com.careertuner.community.event;

/** 게시글 수정 커밋 후 발행 — release 설정 사용자의 리액션 해지 트리거. */
public record PostEditedEvent(Long postId) {}
