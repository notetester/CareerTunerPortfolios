package com.careertuner.billing.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record CurrentRefundPolicyResponse(
        Long id,
        String policyCode,
        int version,
        String title,
        String summary,
        String content,
        String rulesJson,
        LocalDateTime effectiveAt,
        Long noticeId,
        Set<String> acknowledgedTriggers
) {
}
