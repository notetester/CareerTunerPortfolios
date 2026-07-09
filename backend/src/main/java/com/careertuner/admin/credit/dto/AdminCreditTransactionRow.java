package com.careertuner.admin.credit.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminCreditTransactionRow {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private String type;
    private int amount;
    private int balanceAfter;
    private String featureType;
    private Long aiUsageLogId;
    private String reason;
    private LocalDateTime createdAt;
}
