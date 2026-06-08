package com.careertuner.applicationcase.service;

import java.util.List;

import com.careertuner.applicationcase.dto.AnalysisResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.dto.CompanyAnalysisReviewRequest;
import com.careertuner.applicationcase.dto.CreateApplicationCaseRequest;
import com.careertuner.jobanalysis.dto.JobAnalysisReviewRequest;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobposting.dto.JobPostingRequest;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.applicationcase.dto.UpdateApplicationCaseRequest;
import org.springframework.web.multipart.MultipartFile;

public interface ApplicationCaseService {

    ApplicationCaseResponse create(Long userId, CreateApplicationCaseRequest request);

    List<ApplicationCaseResponse> list(Long userId, boolean includeArchived);

    ApplicationCaseResponse get(Long userId, Long id);

    ApplicationCaseResponse update(Long userId, Long id, UpdateApplicationCaseRequest request);

    void delete(Long userId, Long id);

    JobPostingResponse saveJobPosting(Long userId, Long applicationCaseId, JobPostingRequest request);

    JobPostingResponse uploadJobPostingFile(Long userId, Long applicationCaseId, MultipartFile file, String sourceType);

    JobPostingResponse getJobPosting(Long userId, Long applicationCaseId);

    List<JobPostingResponse> getJobPostingRevisions(Long userId, Long applicationCaseId);

    JobAnalysisResponse createMockJobAnalysis(Long userId, Long applicationCaseId);

    JobAnalysisResponse createJobAnalysis(Long userId, Long applicationCaseId);

    JobAnalysisResponse getJobAnalysis(Long userId, Long applicationCaseId);

    List<JobAnalysisResponse> getJobAnalysisHistory(Long userId, Long applicationCaseId);

    JobAnalysisResponse reviewJobAnalysis(Long userId, Long applicationCaseId, Long analysisId, JobAnalysisReviewRequest request);

    CompanyAnalysisResponse createMockCompanyAnalysis(Long userId, Long applicationCaseId);

    CompanyAnalysisResponse createCompanyAnalysis(Long userId, Long applicationCaseId);

    CompanyAnalysisResponse getCompanyAnalysis(Long userId, Long applicationCaseId);

    List<CompanyAnalysisResponse> getCompanyAnalysisHistory(Long userId, Long applicationCaseId);

    CompanyAnalysisResponse reviewCompanyAnalysis(Long userId, Long applicationCaseId, Long analysisId, CompanyAnalysisReviewRequest request);

    AnalysisResponse createMockAnalysis(Long userId, Long applicationCaseId);

    AnalysisResponse getAnalysis(Long userId, Long applicationCaseId);
}
