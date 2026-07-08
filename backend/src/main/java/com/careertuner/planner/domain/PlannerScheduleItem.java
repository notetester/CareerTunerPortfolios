package com.careertuner.planner.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerScheduleItem {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String kind;
    private String status;
    private boolean allDay;
    private String timingPrecision;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String timezone;
    private Long applicationCaseId;
    private Long fitAnalysisId;
    private String sourceType;
    private String sourceRef;
    private String sourceSnapshotJson;
    private boolean overlayVisible;
    private double opacity;
    private boolean pinned;
    private boolean clickThrough;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String applicationCompanyName;
    private String applicationJobTitle;
    private Integer fitScore;
    private LocalDate applicationDeadlineDate;
}
