package com.careertuner.planner.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PlannerStrategyDraftItemResponse(
        String title,
        String description,
        String kind,
        boolean allDay,
        String timingPrecision,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String timezone,
        Long applicationCaseId,
        Long fitAnalysisId,
        String sourceType,
        String sourceRef,
        String sourceSnapshotJson,
        List<PlannerScheduleReminderRequest> reminders,
        int overlapCount
) {
}
