package com.careertuner.admin.correction.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminCorrectionDetail {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private Long applicationCaseId;
    private String companyName;
    private String jobTitle;
    private String correctionType;
    private String sourceType;
    private Long sourceRefId;
    private String originalText;
    private String improvedText;
    private String resultJson;
    private String status;
    private Long aiUsageLogId;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Integer creditUsed;
    private String adminMemo;
    private LocalDateTime createdAt;
}
