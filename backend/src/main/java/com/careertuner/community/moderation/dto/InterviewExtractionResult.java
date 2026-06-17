package com.careertuner.community.moderation.dto;

import java.util.List;

public record InterviewExtractionResult(
        String company,
        String position,
        String interviewDate,
        String resultStatus,
        List<ExtractedQuestion> questions,
        String overallNote
) {
    public record ExtractedQuestion(
            String question,
            String questionType,
            String context,
            List<String> followUps
    ) {}
}
