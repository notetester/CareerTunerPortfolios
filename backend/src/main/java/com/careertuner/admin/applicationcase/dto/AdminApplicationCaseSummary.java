package com.careertuner.admin.applicationcase.dto;

import lombok.Data;

@Data
public class AdminApplicationCaseSummary {
    private long totalCount;
    private long draftCount;
    private long analyzingCount;
    private long readyCount;
    private long appliedCount;
    private long closedCount;
    private long missingJobAnalysisCount;
    private long missingCompanyAnalysisCount;
    private long missingAnyAnalysisCount;
    private long completeAnalysisCount;
    private long failedUsageCount;
}
