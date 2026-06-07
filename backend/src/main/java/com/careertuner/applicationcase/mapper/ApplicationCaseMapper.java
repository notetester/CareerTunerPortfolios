package com.careertuner.applicationcase.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.CompanyAnalysis;
import com.careertuner.applicationcase.domain.FitAnalysis;
import com.careertuner.applicationcase.domain.JobAnalysis;
import com.careertuner.applicationcase.domain.JobPosting;

@Mapper
public interface ApplicationCaseMapper {

    void insertApplicationCase(ApplicationCase applicationCase);

    List<ApplicationCase> findApplicationCasesByUserId(Long userId);

    ApplicationCase findApplicationCaseByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int updateApplicationCase(ApplicationCase applicationCase);

    int deleteApplicationCase(@Param("id") Long id, @Param("userId") Long userId);

    void deleteJobPostingsByCaseId(Long applicationCaseId);

    void insertJobPosting(JobPosting jobPosting);

    JobPosting findLatestJobPostingByCaseId(Long applicationCaseId);

    void deleteJobAnalysesByCaseId(Long applicationCaseId);

    void insertJobAnalysis(JobAnalysis jobAnalysis);

    JobAnalysis findLatestJobAnalysisByCaseId(Long applicationCaseId);

    void deleteCompanyAnalysesByCaseId(Long applicationCaseId);

    void insertCompanyAnalysis(CompanyAnalysis companyAnalysis);

    CompanyAnalysis findLatestCompanyAnalysisByCaseId(Long applicationCaseId);

    void deleteFitAnalysesByCaseId(Long applicationCaseId);

    void insertFitAnalysis(FitAnalysis fitAnalysis);

    FitAnalysis findLatestFitAnalysisByCaseId(Long applicationCaseId);

    void insertAiUsageLog(AiUsageLog aiUsageLog);
}
