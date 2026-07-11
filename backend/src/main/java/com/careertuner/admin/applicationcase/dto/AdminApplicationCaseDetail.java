package com.careertuner.admin.applicationcase.dto;

import java.util.List;

import com.careertuner.admin.aiusage.dto.AdminAiUsageLogRow;
import com.careertuner.applicationcase.domain.ApplicationCaseStatusHistory;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobposting.dto.JobPostingResponse;

public record AdminApplicationCaseDetail(
        AdminApplicationCaseRow applicationCase,
        List<JobPostingResponse> jobPostings,
        List<JobAnalysisResponse> jobAnalyses,
        List<CompanyAnalysisResponse> companyAnalyses,
        List<AdminAiUsageLogRow> usageLogs,
        /** 상태 변경 타임라인(최신순) — 관리자 상태 변경 기록 노출 */
        List<ApplicationCaseStatusHistory> statusHistory
) {
}
