package com.careertuner.planner.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.planner.domain.PlannerScheduleItem;

public record PlannerScheduleItemResponse(
        Long id,
        String title,
        String description,
        String kind,
        String status,
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
        boolean overlayVisible,
        double opacity,
        boolean pinned,
        boolean clickThrough,
        String applicationCompanyName,
        String applicationJobTitle,
        Integer fitScore,
        LocalDate applicationDeadlineDate,
        List<PlannerScheduleReminderResponse> reminders,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PlannerScheduleItemResponse of(
            PlannerScheduleItem item,
            List<PlannerScheduleReminderResponse> reminders) {
        return new PlannerScheduleItemResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getKind(),
                item.getStatus(),
                item.isAllDay(),
                item.getTimingPrecision(),
                item.getStartAt(),
                item.getEndAt(),
                item.getTimezone(),
                item.getApplicationCaseId(),
                item.getFitAnalysisId(),
                item.getSourceType(),
                item.getSourceRef(),
                item.getSourceSnapshotJson(),
                item.isOverlayVisible(),
                item.getOpacity(),
                item.isPinned(),
                item.isClickThrough(),
                item.getApplicationCompanyName(),
                item.getApplicationJobTitle(),
                item.getFitScore(),
                item.getApplicationDeadlineDate(),
                reminders,
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
