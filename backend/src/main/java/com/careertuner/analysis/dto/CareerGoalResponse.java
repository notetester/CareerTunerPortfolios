package com.careertuner.analysis.dto;

import java.time.LocalDateTime;

import com.careertuner.analysis.domain.CareerGoal;

public record CareerGoalResponse(
        Long id,
        String targetJob,
        String targetPeriod,
        String prioritySkill,
        String preferredCompanyType,
        LocalDateTime updatedAt
) {
    public static CareerGoalResponse from(CareerGoal goal) {
        return goal == null ? null : new CareerGoalResponse(
                goal.getId(), goal.getTargetJob(), goal.getTargetPeriod(), goal.getPrioritySkill(),
                goal.getPreferredCompanyType(), goal.getUpdatedAt());
    }
}
