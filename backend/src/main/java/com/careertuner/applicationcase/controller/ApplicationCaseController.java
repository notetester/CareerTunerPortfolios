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
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.applicationcase.dto.AnalysisResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.dto.CompanyAnalysisResponse;
import com.careertuner.applicationcase.dto.CreateApplicationCaseRequest;
import com.careertuner.applicationcase.dto.JobAnalysisResponse;
import com.careertuner.applicationcase.dto.JobPostingRequest;
import com.careertuner.applicationcase.dto.JobPostingResponse;
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

    @GetMapping
    public ApiResponse<List<ApplicationCaseResponse>> list(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(applicationCaseService.list(authUser.id()));
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

    @PostMapping("/{id}/job-posting")
    public ApiResponse<JobPostingResponse> saveJobPosting(@AuthenticationPrincipal AuthUser authUser,
                                                          @PathVariable Long id,
                                                          @Valid @RequestBody JobPostingRequest request) {
        return ApiResponse.ok(applicationCaseService.saveJobPosting(authUser.id(), id, request));
    }

    @GetMapping("/{id}/job-posting")
    public ApiResponse<JobPostingResponse> getJobPosting(@AuthenticationPrincipal AuthUser authUser,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getJobPosting(authUser.id(), id));
    }

    @PostMapping("/{id}/job-analysis/mock")
    public ApiResponse<JobAnalysisResponse> createMockJobAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                                  @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.createMockJobAnalysis(authUser.id(), id));
    }

    @GetMapping("/{id}/job-analysis")
    public ApiResponse<JobAnalysisResponse> getJobAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getJobAnalysis(authUser.id(), id));
    }

    @PostMapping("/{id}/company-analysis/mock")
    public ApiResponse<CompanyAnalysisResponse> createMockCompanyAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                                          @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.createMockCompanyAnalysis(authUser.id(), id));
    }

    @GetMapping("/{id}/company-analysis")
    public ApiResponse<CompanyAnalysisResponse> getCompanyAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                                   @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getCompanyAnalysis(authUser.id(), id));
    }

    @PostMapping("/{id}/analysis/mock")
    public ApiResponse<AnalysisResponse> createMockAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                            @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.createMockAnalysis(authUser.id(), id));
    }

    @GetMapping("/{id}/analysis")
    public ApiResponse<AnalysisResponse> getAnalysis(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long id) {
        return ApiResponse.ok(applicationCaseService.getAnalysis(authUser.id(), id));
    }
}
