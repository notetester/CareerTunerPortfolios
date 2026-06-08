package com.careertuner.admin.aiusage.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminAiUsageLogRow {

    private Long id;
    private Long userId;
    private String userEmail;
    private Long applicationCaseId;
    private String companyName;
    private String jobTitle;
    private String featureType;
    private String status;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer tokenUsage;
    private Integer creditUsed;
    private String errorMessage;
    private LocalDateTime createdAt;
}
