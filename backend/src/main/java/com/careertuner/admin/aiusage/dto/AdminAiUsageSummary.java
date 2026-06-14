package com.careertuner.admin.aiusage.dto;

import lombok.Data;

@Data
public class AdminAiUsageSummary {
    private long totalCount;
    private long successCount;
    private long failedCount;
    private long tokenUsage;
    private long creditUsed;
    private long jobAnalysisCount;
    private long companyResearchCount;
    private long jobPostingOcrCount;
}
