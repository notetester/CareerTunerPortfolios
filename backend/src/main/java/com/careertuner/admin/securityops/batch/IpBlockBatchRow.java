package com.careertuner.admin.securityops.batch;

import java.time.LocalDateTime;

/** IP 정책 배치 조회 응답. */
public record IpBlockBatchRow(
        Long id,
        String batchCode,
        String batchName,
        String sourceType,
        String sourceName,
        String ruleAction,
        int defaultPriority,
        boolean active,
        int totalRuleCount,
        int activeRuleCount,
        String memo,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
