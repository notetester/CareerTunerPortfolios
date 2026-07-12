package com.careertuner.interview.dto;

import java.util.List;

public record InterviewReportResponse(
        int totalScore,
        Integer previousScore,
        int questionCount,
        String durationLabel,
        List<Category> categories,
        List<String> summaryFeedback,
        List<QuestionScore> questionScores) {

    public record Category(String label, int score) {
    }

    /** 질문별 채점 결과 — 음성/영상 면접도 텍스트와 동일하게 질문 단위 점수·피드백을 리포트에서 노출한다. */
    public record QuestionScore(Long questionId, int order, String question, Integer score, String feedback,
                                Integer voiceScore, Integer visualScore) {
    }

    /** 캐시된 리포트 스냅샷(질문별 점수 미포함일 수 있음)에 현재 답변 기준 질문별 채점을 덧입힌다. */
    public InterviewReportResponse withQuestionScores(List<QuestionScore> questionScores) {
        return new InterviewReportResponse(
                totalScore, previousScore, questionCount, durationLabel, categories, summaryFeedback, questionScores);
    }
}
