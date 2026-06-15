package com.careertuner.dashboard.domain;

import lombok.Data;

@Data
public class DashboardWeeklyMetricSource {
    private Integer currentFitAverage;
    private Integer previousFitAverage;
    private Integer currentGapCount;
    private Integer previousGapCount;
    private Integer currentInterviewAverage;
    private Integer previousInterviewAverage;
}
