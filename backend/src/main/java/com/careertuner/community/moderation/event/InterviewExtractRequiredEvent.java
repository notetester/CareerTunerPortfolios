package com.careertuner.community.moderation.event;

/**
 * "이 면접후기 게시글에서 AI 질문 추출이 필요하다"는 이벤트.
 * 리스너가 extractInterviewQuestions()를 호출한다.
 */
public record InterviewExtractRequiredEvent(Long postId) {}
