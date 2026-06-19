package com.careertuner.community.moderation.event;

/**
 * "이 게시글이 신고되었으니 AI 분류가 필요하다"는 이벤트.
 * 리스너가 classify()를 호출한다 (moderate()가 아님).
 */
public record ReportClassifyRequiredEvent(Long postId) {}
