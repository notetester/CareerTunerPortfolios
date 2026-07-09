package com.careertuner.interview.dto;

/**
 * 면접 진행 상태. AI 면접관 대화 진행(다음 질문/종료 판단)의 기준 데이터.
 * 답변 유무로 진행도를 계산하며, 다음에 답할 질문을 함께 내려준다.
 */
public record InterviewProgressResponse(
        Long sessionId,
        int totalQuestions,
        int answeredQuestions,
        boolean finished,
        InterviewQuestionResponse currentQuestion) {
}
