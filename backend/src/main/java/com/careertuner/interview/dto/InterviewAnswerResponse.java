package com.careertuner.interview.dto;

import java.time.LocalDateTime;

import com.careertuner.interview.domain.InterviewAnswer;

public record InterviewAnswerResponse(
        Long id,
        Long questionId,
        String answerText,
        String audioUrl,
        String videoUrl,
        Integer score,
        String feedback,
        String improvedAnswer,
        LocalDateTime createdAt) {

    public static InterviewAnswerResponse from(InterviewAnswer a) {
        return new InterviewAnswerResponse(
                a.getId(),
                a.getQuestionId(),
                a.getAnswerText(),
                a.getAudioUrl(),
                a.getVideoUrl(),
                a.getScore(),
                a.getFeedback(),
                a.getImprovedAnswer(),
                a.getCreatedAt());
    }
}
