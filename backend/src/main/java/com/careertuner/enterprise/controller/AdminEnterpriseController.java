package com.careertuner.enterprise.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.enterprise.dto.EnterpriseDtos.ApplicationResponse;
import com.careertuner.enterprise.dto.EnterpriseDtos.ApplicationReviewRequest;
import com.careertuner.enterprise.dto.EnterpriseDtos.JobResponse;
import com.careertuner.enterprise.dto.EnterpriseDtos.JobReviewRequest;
import com.careertuner.enterprise.service.EnterpriseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/enterprise")
@RequiredArgsConstructor
@Validated
public class AdminEnterpriseController {

    private final EnterpriseService enterpriseService;

    @GetMapping("/applications")
    public ApiResponse<List<ApplicationResponse>> applications(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return ApiResponse.ok(enterpriseService.adminApplications(authUser, status, keyword, limit));
    }

    @PatchMapping("/applications/{applicationId}")
    public ApiResponse<ApplicationResponse> reviewApplication(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long applicationId,
            @RequestBody ApplicationReviewRequest request) {
        return ApiResponse.ok(enterpriseService.adminReviewApplication(authUser, applicationId, request));
    }

    @GetMapping("/jobs")
    public ApiResponse<List<JobResponse>> jobs(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return ApiResponse.ok(enterpriseService.adminJobs(authUser, status, keyword, limit));
    }

    @PatchMapping("/jobs/{jobId}/review")
    public ApiResponse<JobResponse> reviewJob(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long jobId,
            @RequestBody JobReviewRequest request) {
        return ApiResponse.ok(enterpriseService.adminReviewJob(authUser, jobId, request));
    }
}
