package com.careertuner.admin.fitanalysis.dto;

import java.time.LocalDateTime;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisMemo;

public record AdminFitAnalysisMemoResponse(
        Long id,
        Long fitAnalysisId,
        Long adminUserId,
        String adminName,
        String adminEmail,
        String memoType,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminFitAnalysisMemoResponse from(AdminFitAnalysisMemo memo) {
        return new AdminFitAnalysisMemoResponse(
                memo.getId(),
                memo.getFitAnalysisId(),
                memo.getAdminUserId(),
                memo.getAdminName(),
                memo.getAdminEmail(),
                memo.getMemoType(),
                memo.getContent(),
                memo.getCreatedAt(),
                memo.getUpdatedAt());
    }
}
