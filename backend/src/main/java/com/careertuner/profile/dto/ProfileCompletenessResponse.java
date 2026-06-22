package com.careertuner.profile.dto;

import java.util.List;

public record ProfileCompletenessResponse(
        int score,
        List<String> completed,
        List<String> missing,
        List<String> recommendations,
        String jobFamily,
        String jobFamilyLabel,
        List<ProfileCriterionScoreResponse> criteria,
        String model,
        String status
) {
}
