package com.careertuner.analysis.dto;

public record InterviewTrendResponse(
        int totalSessions,
        int averageSessionScore,
        int totalAnswers,
        int averageAnswerScore
) {
}
