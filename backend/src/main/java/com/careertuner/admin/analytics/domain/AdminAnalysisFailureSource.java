package com.careertuner.admin.analytics.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 분석 실패 큐 원천. fit_analysis 와 career_analysis_run 의 비정상(FAILED/FALLBACK) 결과를
 * 하나의 목록으로 합쳐 관리자가 원인별로 점검할 수 있게 한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAnalysisFailureSource {

    private String source;            // FIT_ANALYSIS / CAREER_TREND / DASHBOARD_SUMMARY
    private Long refId;
    private String userName;
    private String userEmail;
    private String companyName;       // career_analysis_run 항목은 null
    private String jobTitle;          // career_analysis_run 항목은 null
    private String status;            // FAILED / FALLBACK
    private String errorMessage;
    private String model;
    private boolean retryable;
    private LocalDateTime createdAt;
}
