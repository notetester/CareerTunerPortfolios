package com.careertuner.applicationcase.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import com.careertuner.applicationcase.dto.AnalysisResponse;
import com.careertuner.applicationcase.dto.AiUsageFailureResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseExtractionResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseFromJobPostingResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.dto.CompanyAnalysisReviewRequest;
import com.careertuner.applicationcase.dto.CreateApplicationCaseFromJobPostingRequest;
import com.careertuner.applicationcase.dto.CreateApplicationCaseRequest;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.dto.JobAnalysisReviewRequest;
import com.careertuner.jobposting.dto.JobPostingRequest;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.applicationcase.dto.UpdateApplicationCaseRequest;
import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/application-cases")
@RequiredArgsConstructor
public class ApplicationCaseController {

    private final ApplicationCaseService applicationCaseService;

    @PostMapping
    public ApiResponse<ApplicationCaseResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                       @Valid @RequestBody CreateApplicationCaseRequest request) {
        return ApiResponse.ok(applicationCaseService.create(authUser.id(), request));
    }

    @PostMapping("/from-job-posting")
    public ApiResponse<ApplicationCaseFromJobPostingResponse> createFromJobPosting(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateApplicationCaseFromJobPostingRequest request) {
        return ApiResponse.ok(applicationCaseService.createFromJobPosting(authUser.id(), request));
    }

    @PostMapping(value = "/from-job-posting/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ApplicationCaseFromJobPostingResponse> createFromJobPostingUpload(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceType") String sourceType,
            @RequestParam(defaultValue = "false") boolean favorite) {
        return ApiResponse.ok(applicationCaseService.createFromJobPostingUpload(authUser.id(), file, sourceType, favorite));
    }

    @GetMapping
    public ApiResponse<List<ApplicationCaseResponse>> list(@AuthenticationPrincipal AuthUser authUser,
                                                           @RequestParam(required = false) String view,
                                                           @RequestParam(defaultValue = "false") boolean includeArchived) {
        return ApiResponse.ok(applicationCaseService.list(authUser.id(), view, includeArchived));
    }

    @GetMapping("/extractions/active")
    public ApiResponse<List<ApplicationCaseExtractionResponse>> getActiveExtractions(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(applicationCaseService.getActiveExtractions(authUser.id()));
    }

    @GetMapping("/job-posting/extractions/latest")
    public ApiResponse<List<ApplicationCaseExtractionResponse>> getLatestJobPostingExtractions(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) List<Long> applicationCaseIds) {
        return ApiResponse.ok(applicationCaseService.getLatestJobPostingExtractions(authUser.id(), applicationCaseIds));
    }

    @GetMapping("/{id}")
    public ApiResponse<ApplicationCaseResponse> get(@AuthenticationPrincipal AuthUser authUser,
                                                    @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.get(authUser.id(), id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ApplicationCaseResponse> update(@AuthenticationPrincipal AuthUser authUser,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody UpdateApplicationCaseRequest request) {
        return ApiResponse.ok(applicationCaseService.update(authUser.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long id) {
        applicationCaseService.delete(authUser.id(), id);
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/restore")
    public ApiResponse<Void> restore(@AuthenticationPrincipal AuthUser authUser,
                                     @PathVariable Long id) {
        applicationCaseService.restore(authUser.id(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/job-posting")
    public ApiResponse<JobPostingResponse> saveJobPosting(@AuthenticationPrincipal AuthUser authUser,
                                                          @PathVariable Long id,
                                                          @Valid @RequestBody JobPostingRequest request) {
        return ApiResponse.ok(applicationCaseService.saveJobPosting(authUser.id(), id, request));
    }

    @PostMapping(value = "/{id}/job-posting/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JobPostingResponse> uploadJobPostingFile(@AuthenticationPrincipal AuthUser authUser,
                                                                @PathVariable Long id,
                                                                @RequestParam("file") MultipartFile file,
                                                                @RequestParam("sourceType") String sourceType) {
        return ApiResponse.ok(applicationCaseService.uploadJobPostingFile(authUser.id(), id, file, sourceType));
    }

    @GetMapping("/{id}/job-posting")
    public ApiResponse<JobPostingResponse> getJobPosting(@AuthenticationPrincipal AuthUser authUser,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getJobPosting(authUser.id(), id));
    }

    @GetMapping("/{id}/job-posting/revisions")
    public ApiResponse<List<JobPostingResponse>> getJobPostingRevisions(@AuthenticationPrincipal AuthUser authUser,
                                                                        @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getJobPostingRevisions(authUser.id(), id));
    }

    @GetMapping("/{id}/job-posting/extraction")
    public ApiResponse<ApplicationCaseExtractionResponse> getJobPostingExtraction(@AuthenticationPrincipal AuthUser authUser,
                                                                                  @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getLatestJobPostingExtraction(authUser.id(), id));
    }

    @PostMapping("/{id}/job-posting/extraction/retry")
    public ApiResponse<ApplicationCaseExtractionResponse> retryJobPostingExtraction(@AuthenticationPrincipal AuthUser authUser,
                                                                                   @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.retryJobPostingExtraction(authUser.id(), id));
    }

    @PostMapping("/{id}/job-analysis")
    public ApiResponse<JobAnalysisResponse> createJobAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.createJobAnalysis(authUser.id(), id));
    }

    @GetMapping("/{id}/job-analysis")
    public ApiResponse<JobAnalysisResponse> getJobAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getJobAnalysis(authUser.id(), id));
    }

    @GetMapping("/{id}/job-analysis/history")
    public ApiResponse<List<JobAnalysisResponse>> getJobAnalysisHistory(@AuthenticationPrincipal AuthUser authUser,
                                                                        @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getJobAnalysisHistory(authUser.id(), id));
    }

    @PatchMapping("/{id}/job-analysis/{analysisId}/review")
    public ApiResponse<JobAnalysisResponse> reviewJobAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long id,
                                                              @PathVariable Long analysisId,
                                                              @Valid @RequestBody JobAnalysisReviewRequest request) {
        return ApiResponse.ok(applicationCaseService.reviewJobAnalysis(authUser.id(), id, analysisId, request));
    }

    @PostMapping("/{id}/company-analysis")
    public ApiResponse<CompanyAnalysisResponse> createCompanyAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                                      @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.createCompanyAnalysis(authUser.id(), id));
    }

    @GetMapping("/{id}/company-analysis")
    public ApiResponse<CompanyAnalysisResponse> getCompanyAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                                   @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getCompanyAnalysis(authUser.id(), id));
    }

    @GetMapping("/{id}/company-analysis/history")
    public ApiResponse<List<CompanyAnalysisResponse>> getCompanyAnalysisHistory(@AuthenticationPrincipal AuthUser authUser,
                                                                                @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getCompanyAnalysisHistory(authUser.id(), id));
    }

    @PatchMapping("/{id}/company-analysis/{analysisId}/review")
    public ApiResponse<CompanyAnalysisResponse> reviewCompanyAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                                      @PathVariable Long id,
                                                                      @PathVariable Long analysisId,
                                                                      @Valid @RequestBody CompanyAnalysisReviewRequest request) {
        return ApiResponse.ok(applicationCaseService.reviewCompanyAnalysis(authUser.id(), id, analysisId, request));
    }

    @GetMapping("/{id}/analysis")
    public ApiResponse<AnalysisResponse> getAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getAnalysis(authUser.id(), id));
    }

    @GetMapping("/{id}/ai-usage/b/failures")
    public ApiResponse<List<AiUsageFailureResponse>> getAiUsageFailures(@AuthenticationPrincipal AuthUser authUser,
                                                                        @PathVariable Long id,
                                                                        @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(applicationCaseService.getAiUsageFailures(authUser.id(), id, limit));
    }
}
