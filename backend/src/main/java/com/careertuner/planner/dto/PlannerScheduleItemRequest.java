package com.careertuner.planner.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlannerScheduleItemRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @Size(max = 30) String kind,
        @Size(max = 30) String status,
        Boolean allDay,
        @Size(max = 20) String timingPrecision,
        LocalDateTime startAt,
        LocalDateTime endAt,
        @Size(max = 64) String timezone,
        Long applicationCaseId,
        Long fitAnalysisId,
        @Size(max = 40) String sourceType,
        @Size(max = 120) String sourceRef,
        String sourceSnapshotJson,
        Boolean overlayVisible,
        Double opacity,
        Boolean pinned,
        Boolean clickThrough,
        @Valid List<PlannerScheduleReminderRequest> reminders
) {
}
