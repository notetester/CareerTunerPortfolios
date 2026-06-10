package com.careertuner.interview.dto;

import com.careertuner.interview.domain.InterviewQuestion;

public record InterviewQuestionResponse(
        Long id,
        Long interviewSessionId,
        String question,
        String questionType,
        Integer sortOrder) {

    public static InterviewQuestionResponse from(InterviewQuestion q) {
        return new InterviewQuestionResponse(
                q.getId(),
                q.getInterviewSessionId(),
                q.getQuestion(),
                q.getQuestionType(),
                q.getSortOrder());
    }
}
