package com.careertuner.interview.dto;

import java.util.List;

public record InterviewReportResponse(
        int totalScore,
        Integer previousScore,
        int questionCount,
        String durationLabel,
        List<Category> categories,
        List<String> summaryFeedback) {

    public record Category(String label, int score) {
    }
}
