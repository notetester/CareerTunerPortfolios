package com.careertuner.admin.applicationcase.dto;

import java.util.List;

import com.careertuner.admin.aiusage.dto.AdminAiUsageLogRow;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobposting.dto.JobPostingResponse;

public record AdminApplicationCaseDetail(
        AdminApplicationCaseRow applicationCase,
        List<JobPostingResponse> jobPostings,
        List<JobAnalysisResponse> jobAnalyses,
        List<CompanyAnalysisResponse> companyAnalyses,
        List<AdminAiUsageLogRow> usageLogs
) {
}
