package com.careertuner.applicationcase.service;

import java.util.List;

import com.careertuner.applicationcase.dto.AnalysisResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.dto.CompanyAnalysisResponse;
import com.careertuner.applicationcase.dto.CreateApplicationCaseRequest;
import com.careertuner.applicationcase.dto.JobAnalysisResponse;
import com.careertuner.applicationcase.dto.JobPostingRequest;
import com.careertuner.applicationcase.dto.JobPostingResponse;
import com.careertuner.applicationcase.dto.UpdateApplicationCaseRequest;

public interface ApplicationCaseService {

    ApplicationCaseResponse create(Long userId, CreateApplicationCaseRequest request);

    List<ApplicationCaseResponse> list(Long userId);

    ApplicationCaseResponse get(Long userId, Long id);

    ApplicationCaseResponse update(Long userId, Long id, UpdateApplicationCaseRequest request);

    void delete(Long userId, Long id);

    JobPostingResponse saveJobPosting(Long userId, Long applicationCaseId, JobPostingRequest request);

    JobPostingResponse getJobPosting(Long userId, Long applicationCaseId);

    JobAnalysisResponse createMockJobAnalysis(Long userId, Long applicationCaseId);

    JobAnalysisResponse getJobAnalysis(Long userId, Long applicationCaseId);

    CompanyAnalysisResponse createMockCompanyAnalysis(Long userId, Long applicationCaseId);

    CompanyAnalysisResponse getCompanyAnalysis(Long userId, Long applicationCaseId);

    AnalysisResponse createMockAnalysis(Long userId, Long applicationCaseId);

    AnalysisResponse getAnalysis(Long userId, Long applicationCaseId);
}
