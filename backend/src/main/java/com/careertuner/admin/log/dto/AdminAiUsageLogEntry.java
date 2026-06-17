package com.careertuner.admin.log.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 시스템 전체 AI 사용 로그 한 줄(ai_usage_log + users 조인).
 * (admin.aiusage 의 B 전용 로그 DTO 와 단순명이 겹치지 않도록 Entry 로 명명.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAiUsageLogEntry {

    private Long id;
    private Long userId;
    private String userEmail;
    private String featureType;
    private String status;
    private String model;
    private Integer tokenUsage;
    private Integer creditUsed;
    private String errorMessage;
    private LocalDateTime createdAt;
}
