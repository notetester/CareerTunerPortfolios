package com.careertuner.billing.dto;

import java.time.LocalDateTime;

import com.careertuner.billing.domain.RefundPolicy;

public record RefundPolicyResponse(
        Long id,
        String policyCode,
        int version,
        String title,
        String summary,
        String content,
        String rulesJson,
        String status,
        boolean adverse,
        LocalDateTime effectiveAt,
        LocalDateTime publishedAt,
        Long noticeId,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RefundPolicyResponse from(RefundPolicy policy) {
        return new RefundPolicyResponse(
                policy.getId(),
                policy.getPolicyCode(),
                policy.getVersion(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getContent(),
                policy.getRulesJson(),
                policy.getStatus(),
                policy.isAdverse(),
                policy.getEffectiveAt(),
                policy.getPublishedAt(),
                policy.getNoticeId(),
                policy.getCreatedBy(),
                policy.getCreatedAt(),
                policy.getUpdatedAt());
    }
}
