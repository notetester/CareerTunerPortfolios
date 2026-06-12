package com.careertuner.admin.analytics.dto;

import java.time.LocalDateTime;

import com.careertuner.admin.analytics.domain.AdminAnalysisFailureSource;

/** 분석 실패 큐 항목. */
public record AdminAnalysisFailureResponse(
        String source,
        Long refId,
        String userName,
        String userEmail,
        String companyName,
        String jobTitle,
        String status,
        String errorMessage,
        String model,
        boolean retryable,
        LocalDateTime createdAt
) {
    public static AdminAnalysisFailureResponse from(AdminAnalysisFailureSource source) {
        return new AdminAnalysisFailureResponse(
                source.getSource(),
                source.getRefId(),
                source.getUserName(),
                source.getUserEmail(),
                source.getCompanyName(),
                source.getJobTitle(),
                source.getStatus(),
                source.getErrorMessage(),
                source.getModel(),
                source.isRetryable(),
                source.getCreatedAt());
    }
}
