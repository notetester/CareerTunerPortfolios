package com.careertuner.analysis.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisWeeklyMetricSource {
    private Integer currentFitAverage;
    private Integer previousFitAverage;
    private Integer currentGapCount;
    private Integer previousGapCount;
    private Integer currentInterviewAverage;
    private Integer previousInterviewAverage;
}
