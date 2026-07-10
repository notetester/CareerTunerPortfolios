package com.careertuner.applicationcase.service;

import java.util.List;

import com.careertuner.applicationcase.dto.AnalysisResponse;
import com.careertuner.applicationcase.dto.AiUsageFailureResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseExtractionResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseFromJobPostingResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.dto.CompanyAnalysisReviewRequest;
import com.careertuner.applicationcase.dto.CreateApplicationCaseFromJobPostingRequest;
import com.careertuner.applicationcase.dto.CreateApplicationCaseRequest;
import com.careertuner.applicationcase.dto.ConfirmJobPostingExtractionRequest;
import com.careertuner.applicationcase.dto.ReviewJobPostingExtractionRequest;
import com.careertuner.jobanalysis.dto.JobAnalysisReviewRequest;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobposting.dto.JobPostingRequest;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.applicationcase.dto.UpdateApplicationCaseRequest;
import org.springframework.web.multipart.MultipartFile;

public interface ApplicationCaseService {

    ApplicationCaseResponse create(Long userId, CreateApplicationCaseRequest request);

    ApplicationCaseFromJobPostingResponse createFromJobPosting(Long userId,
                                                               CreateApplicationCaseFromJobPostingRequest request);

    ApplicationCaseFromJobPostingResponse createFromJobPostingUpload(Long userId,
                                                                     MultipartFile file,
                                                                     String sourceType,
                                                                     Boolean favorite,
                                                                     String jobAnalysisProvider,
                                                                     String companyAnalysisProvider);

    List<ApplicationCaseResponse> list(Long userId, String view, boolean includeArchived);

    ApplicationCaseResponse get(Long userId, Long id);

    ApplicationCaseResponse update(Long userId, Long id, UpdateApplicationCaseRequest request);

    void delete(Long userId, Long id);

    void restore(Long userId, Long id);

    void hideFromTrash(Long userId, Long id);

    JobPostingResponse saveJobPosting(Long userId, Long applicationCaseId, JobPostingRequest request);

    JobPostingResponse uploadJobPostingFile(Long userId, Long applicationCaseId, MultipartFile file, String sourceType);

    JobPostingResponse getJobPosting(Long userId, Long applicationCaseId);

    List<JobPostingResponse> getJobPostingRevisions(Long userId, Long applicationCaseId);

    List<ApplicationCaseExtractionResponse> getActiveExtractions(Long userId);

    ApplicationCaseExtractionResponse getLatestJobPostingExtraction(Long userId, Long applicationCaseId);

    List<ApplicationCaseExtractionResponse> getLatestJobPostingExtractions(Long userId, List<Long> applicationCaseIds);

    ApplicationCaseExtractionResponse retryJobPostingExtraction(Long userId, Long applicationCaseId);

    ApplicationCaseExtractionResponse reviewJobPostingExtraction(Long userId,
                                                                 Long applicationCaseId,
                                                                 ReviewJobPostingExtractionRequest request);

    ApplicationCaseExtractionResponse confirmEditedPosting(Long userId,
                                                           Long applicationCaseId,
                                                           ConfirmJobPostingExtractionRequest request);

    JobAnalysisResponse createJobAnalysis(Long userId, Long applicationCaseId);

    JobAnalysisResponse getJobAnalysis(Long userId, Long applicationCaseId);

    List<JobAnalysisResponse> getJobAnalysisHistory(Long userId, Long applicationCaseId);

    JobAnalysisResponse reviewJobAnalysis(Long userId, Long applicationCaseId, Long analysisId, JobAnalysisReviewRequest request);

    CompanyAnalysisResponse createCompanyAnalysis(Long userId, Long applicationCaseId);

    CompanyAnalysisResponse getCompanyAnalysis(Long userId, Long applicationCaseId);

    List<CompanyAnalysisResponse> getCompanyAnalysisHistory(Long userId, Long applicationCaseId);

    CompanyAnalysisResponse reviewCompanyAnalysis(Long userId, Long applicationCaseId, Long analysisId, CompanyAnalysisReviewRequest request);

    AnalysisResponse getAnalysis(Long userId, Long applicationCaseId);

    List<AiUsageFailureResponse> getAiUsageFailures(Long userId, Long applicationCaseId, int limit);
}
