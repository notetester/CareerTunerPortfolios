package com.careertuner.planner.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.planner.domain.PlannerScheduleReminder;

public record PlannerScheduleReminderResponse(
        Long id,
        LocalDateTime remindAt,
        Integer offsetMinutes,
        List<String> channels,
        boolean soundEnabled,
        boolean vibrationEnabled,
        String status,
        LocalDateTime sentAt
) {
    public static PlannerScheduleReminderResponse of(PlannerScheduleReminder reminder, List<String> channels) {
        return new PlannerScheduleReminderResponse(
                reminder.getId(),
                reminder.getRemindAt(),
                reminder.getOffsetMinutes(),
                channels,
                reminder.isSoundEnabled(),
                reminder.isVibrationEnabled(),
                reminder.getStatus(),
                reminder.getSentAt());
    }
}
