package com.careertuner.planner.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PlannerScheduleReminderRequest(
        LocalDateTime remindAt,
        Integer offsetMinutes,
        List<String> channels,
        Boolean soundEnabled,
        Boolean vibrationEnabled
) {
}
