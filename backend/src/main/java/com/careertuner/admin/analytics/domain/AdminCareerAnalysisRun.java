package com.careertuner.admin.analytics.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCareerAnalysisRun {

    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String analysisType;
    private String status;
    private String inputSnapshot;
    private String result;
    private String model;
    private String promptVersion;
    private int tokenUsage;
    private String errorMessage;
    private boolean retryable;
    private LocalDateTime createdAt;
    private int memoCount;
    private LocalDateTime latestMemoAt;
}
