package com.careertuner.interview.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** ai_usage_log 공유 테이블 매핑 (면접 도메인용 자체 복사본). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageLog {

    private Long id;
    private Long userId;
    private Long applicationCaseId;
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
