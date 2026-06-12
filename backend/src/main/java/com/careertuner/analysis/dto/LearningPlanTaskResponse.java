package com.careertuner.analysis.dto;

import java.time.LocalDateTime;

import com.careertuner.analysis.domain.LearningPlanTask;

public record LearningPlanTaskResponse(
        Long id,
        Long learningPlanId,
        String task,
        boolean done,
        int sortOrder,
        LocalDateTime completedAt
) {
    public static LearningPlanTaskResponse from(LearningPlanTask task) {
        return new LearningPlanTaskResponse(
                task.getId(), task.getLearningPlanId(), task.getTask(), task.isDone(), task.getSortOrder(),
                task.getCompletedAt());
    }
}
