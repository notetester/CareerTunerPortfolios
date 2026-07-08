package com.careertuner.planner.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerScheduleReminder {

    private Long id;
    private Long scheduleItemId;
    private LocalDateTime remindAt;
    private Integer offsetMinutes;
    private String channelsJson;
    private boolean soundEnabled;
    private boolean vibrationEnabled;
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
