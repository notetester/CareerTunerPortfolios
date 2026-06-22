package com.careertuner.billing.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record AdminBillingPolicyChangeRequest(
        String targetType,
        String applyMode,
        LocalDateTime effectiveFrom,
        Map<String, Object> nextSnapshot
) {}
