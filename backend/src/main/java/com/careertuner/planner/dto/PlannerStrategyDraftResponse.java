package com.careertuner.planner.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PlannerStrategyDraftResponse(
        Long fitAnalysisId,
        Long applicationCaseId,
        String companyName,
        String jobTitle,
        LocalDateTime generatedAt,
        List<String> staleReasons,
        List<PlannerStrategyDraftItemResponse> items
) {
}
