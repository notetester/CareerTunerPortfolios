package com.careertuner.profile.ai;

import java.util.List;
import java.util.Map;

public record ProfileQualityReport(
        int penaltyScore,
        Map<ScoreCriterion, Integer> criterionPenalties,
        List<String> warnings,
        List<String> recommendations
) {
}
