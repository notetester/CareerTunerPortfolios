package com.careertuner.admin.interview.dto;

import java.time.LocalDateTime;

/** 면접 AI 기능(질문/꼬리질문/평가/리포트) 실패 이력 한 줄. ai_usage_log 기반. */
public record AdminInterviewAiFailureRow(
        Long id,
        Long userId,
        String userEmail,
        Long applicationCaseId,
        String companyName,
        String jobTitle,
        String featureType,
        String errorMessage,
        LocalDateTime createdAt) {
}
