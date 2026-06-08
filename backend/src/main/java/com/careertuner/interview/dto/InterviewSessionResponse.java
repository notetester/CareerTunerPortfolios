package com.careertuner.interview.dto;

import java.time.LocalDateTime;

import com.careertuner.interview.domain.InterviewSession;

public record InterviewSessionResponse(
        Long id,
        Long applicationCaseId,
        String mode,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Integer totalScore,
        LocalDateTime createdAt) {

    public static InterviewSessionResponse from(InterviewSession s) {
        return new InterviewSessionResponse(
                s.getId(),
                s.getApplicationCaseId(),
                s.getMode(),
                s.getStartedAt(),
                s.getEndedAt(),
                s.getTotalScore(),
                s.getCreatedAt());
    }
}
