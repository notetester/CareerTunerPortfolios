package com.careertuner.admin.analytics.dto;

import java.time.LocalDateTime;

import com.careertuner.admin.analytics.domain.AdminCareerRunMemo;

public record AdminCareerRunMemoResponse(
        Long id,
        Long careerAnalysisRunId,
        Long adminUserId,
        String adminName,
        String adminEmail,
        String memoType,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminCareerRunMemoResponse from(AdminCareerRunMemo memo) {
        return new AdminCareerRunMemoResponse(
                memo.getId(),
                memo.getCareerAnalysisRunId(),
                memo.getAdminUserId(),
                memo.getAdminName(),
                memo.getAdminEmail(),
                memo.getMemoType(),
                memo.getContent(),
                memo.getCreatedAt(),
                memo.getUpdatedAt());
    }
}
