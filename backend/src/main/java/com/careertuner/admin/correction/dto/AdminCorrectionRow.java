package com.careertuner.admin.correction.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminCorrectionRow {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private Long applicationCaseId;
    private String companyName;
    private String jobTitle;
    private String correctionType;
    private String sourceType;
    private String status;
    private String model;
    private Integer totalTokens;
    private Integer creditUsed;
    private boolean hasMemo;
    private LocalDateTime createdAt;
}
