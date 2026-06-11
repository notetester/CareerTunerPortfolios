package com.careertuner.profile.dto;

import java.util.List;

public record ProfileCompletenessResponse(
        int score,
        List<String> completed,
        List<String> missing,
        List<String> recommendations
) {
}
