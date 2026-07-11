package com.careertuner.interview.dto;

import java.time.LocalDateTime;

import com.careertuner.interview.domain.InterviewSession;

public record InterviewSessionResponse(
        Long id,
        Long applicationCaseId,
        String mode,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        int totalQuestions,
        int answeredQuestions,
        boolean finished,
        Integer totalScore,
        Integer avgScore,
        Integer avgVoiceScore,
        LocalDateTime lastResumedAt,
        LocalDateTime createdAt) {

    public static InterviewSessionResponse from(InterviewSession s) {
        return new InterviewSessionResponse(
                s.getId(),
                s.getApplicationCaseId(),
                s.getMode(),
                s.getStartedAt(),
                s.getEndedAt(),
                s.getTotalQuestions() == null ? 0 : s.getTotalQuestions(),
                s.getAnsweredQuestions() == null ? 0 : s.getAnsweredQuestions(),
                Boolean.TRUE.equals(s.getFinished()),
                s.getTotalScore(),
                s.getAvgAnswerScore(),
                s.getAvgVoiceScore(),
                s.getLastResumedAt(),
                s.getCreatedAt());
    }
}
