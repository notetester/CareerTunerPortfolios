package com.careertuner.analysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerAnalysisRun {

    private Long id;
    private Long userId;
    private String analysisType;
    private String status;
    private String inputSnapshot;
    private String inputFingerprint;
    private String result;
    private String model;
    private String promptVersion;
    private int inputTokens;
    private int outputTokens;
    private int tokenUsage;
    private String errorMessage;
    private boolean retryable;
    private LocalDateTime createdAt;
}
