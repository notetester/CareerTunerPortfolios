package com.careertuner.analysis.dto;

import java.time.LocalDate;
import java.util.List;

import com.careertuner.analysis.domain.LearningPlan;

public record LearningPlanResponse(
        Long id,
        String title,
        String targetSkill,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        int completionRate,
        List<LearningPlanTaskResponse> tasks
) {
    public static LearningPlanResponse of(LearningPlan plan, List<LearningPlanTaskResponse> tasks) {
        int completed = (int) tasks.stream().filter(LearningPlanTaskResponse::done).count();
        int rate = tasks.isEmpty() ? 0 : (int) Math.round(completed * 100.0 / tasks.size());
        return new LearningPlanResponse(
                plan.getId(), plan.getTitle(), plan.getTargetSkill(), plan.getStartDate(), plan.getEndDate(),
                plan.getStatus(), rate, tasks);
    }
}
