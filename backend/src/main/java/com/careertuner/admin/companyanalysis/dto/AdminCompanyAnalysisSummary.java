package com.careertuner.admin.companyanalysis.dto;

import lombok.Data;

@Data
public class AdminCompanyAnalysisSummary {
    private long totalCount;
    private long confirmedCount;
    private long unconfirmedCount;
    private long refreshDueCount;
    private long missingSourceCount;
    private long checkedCount;
    private long memoCount;
}
