package com.careertuner.analysis.dto;

import java.util.List;

public record CareerPlanResponse(
        CareerGoalResponse goal,
        List<LearningPlanResponse> learningPlans
) {
}
