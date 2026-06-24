package com.careertuner.admin.user.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminUserAiUsageRow {
    private Long id;
    private Long userId;
    private String featureType;
    private String status;
    private String model;
    private int inputTokens;
    private int outputTokens;
    private int tokenUsage;
    private int creditUsed;
    private String errorMessage;
    private LocalDateTime createdAt;
}
