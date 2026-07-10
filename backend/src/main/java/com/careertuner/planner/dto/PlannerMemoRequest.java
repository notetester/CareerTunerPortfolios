package com.careertuner.planner.dto;

import jakarta.validation.constraints.Size;

public record PlannerMemoRequest(
        @Size(max = 120) String title,
        @Size(max = 4000) String content,
        @Size(max = 20) String color,
        Boolean pinned,
        Boolean overlayVisible,
        Double opacity,
        Long applicationCaseId,
        Long fitAnalysisId
) {
}
