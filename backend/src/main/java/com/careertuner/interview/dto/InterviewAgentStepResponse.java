package com.careertuner.interview.dto;

import java.time.LocalDateTime;

import com.careertuner.interview.domain.InterviewAgentStep;

/** 멀티에이전트 진행 단계 한 줄 (AI 사고과정 표시용). */
public record InterviewAgentStepResponse(
        Long id,
        Long questionId,
        Integer stepNo,
        String agent,
        String action,
        String summary,
        String detail,
        LocalDateTime createdAt) {

    public static InterviewAgentStepResponse from(InterviewAgentStep s) {
        return new InterviewAgentStepResponse(
                s.getId(),
                s.getQuestionId(),
                s.getStepNo(),
                s.getAgent(),
                s.getAction(),
                s.getSummary(),
                s.getDetail(),
                s.getCreatedAt());
    }
}
