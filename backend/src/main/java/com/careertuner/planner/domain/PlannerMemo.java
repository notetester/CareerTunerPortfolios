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
public class PlannerMemo {

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String color;
    private boolean pinned;
    private boolean overlayVisible;
    private double opacity;
    private Long applicationCaseId;
    private Long fitAnalysisId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
