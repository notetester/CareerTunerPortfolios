package com.careertuner.fitanalysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FitAnalysisLearningTask {

    private Long id;
    private Long fitAnalysisId;
    private String skill;
    private String title;
    private String practiceTask;
    private String expectedDuration;
    private String priority;
    private int sortOrder;
    private boolean completed;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
