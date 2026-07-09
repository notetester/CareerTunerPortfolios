package com.careertuner.admin.securityops.batch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** IP 정책 배치 생성 요청. */
public record IpBlockBatchRequest(
        @NotBlank @Size(max = 160) String batchName,
        @Size(max = 40) String sourceType,   // 기본 MANUAL
        @Size(max = 200) String sourceName,
        @Size(max = 30) String ruleAction,   // 기본 BLOCK
        Integer defaultPriority,             // 기본 100
        @Size(max = 2000) String memo) {
}
