package com.careertuner.planner.dto;

import java.time.LocalDateTime;

import com.careertuner.planner.domain.PlannerMemo;

public record PlannerMemoResponse(
        Long id,
        String title,
        String content,
        String color,
        boolean pinned,
        boolean overlayVisible,
        double opacity,
        Long applicationCaseId,
        Long fitAnalysisId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PlannerMemoResponse from(PlannerMemo memo) {
        return new PlannerMemoResponse(
                memo.getId(),
                memo.getTitle(),
                memo.getContent(),
                memo.getColor(),
                memo.isPinned(),
                memo.isOverlayVisible(),
                memo.getOpacity(),
                memo.getApplicationCaseId(),
                memo.getFitAnalysisId(),
                memo.getCreatedAt(),
                memo.getUpdatedAt());
    }
}
