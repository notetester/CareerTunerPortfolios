package com.careertuner.admin.correction.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminCorrectionFailureRow {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private Long applicationCaseId;
    private String companyName;
    private String jobTitle;
    private String featureType;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private String errorMessage;
    private LocalDateTime createdAt;
}
