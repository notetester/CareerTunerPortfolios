package com.careertuner.fitanalysis.dto;

import java.time.LocalDateTime;

import com.careertuner.fitanalysis.domain.FitAnalysisLearningTask;

public record FitAnalysisLearningTaskResponse(
        Long id,
        Long fitAnalysisId,
        String skill,
        String title,
        String practiceTask,
        String expectedDuration,
        String priority,
        int sortOrder,
        boolean completed,
        LocalDateTime completedAt
) {
    public static FitAnalysisLearningTaskResponse from(FitAnalysisLearningTask task) {
        return new FitAnalysisLearningTaskResponse(
                task.getId(),
                task.getFitAnalysisId(),
                task.getSkill(),
                task.getTitle(),
                task.getPracticeTask(),
                task.getExpectedDuration(),
                task.getPriority(),
                task.getSortOrder(),
                task.isCompleted(),
                task.getCompletedAt());
    }
}
