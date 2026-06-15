package com.careertuner.admin.jobanalysis.dto;

import lombok.Data;

@Data
public class AdminJobAnalysisSummary {
    private long totalCount;
    private long confirmedCount;
    private long unconfirmedCount;
    private long easyCount;
    private long mediumCount;
    private long hardCount;
    private long unknownDifficultyCount;
    private long memoCount;
}
