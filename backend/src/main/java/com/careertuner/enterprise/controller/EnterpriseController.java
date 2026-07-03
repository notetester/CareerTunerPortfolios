package com.careertuner.enterprise.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.enterprise.dto.EnterpriseDtos.ApplicationRequest;
import com.careertuner.enterprise.dto.EnterpriseDtos.ApplicationResponse;
import com.careertuner.enterprise.dto.EnterpriseDtos.JobRequest;
import com.careertuner.enterprise.dto.EnterpriseDtos.JobResponse;
import com.careertuner.enterprise.dto.EnterpriseDtos.StatusResponse;
import com.careertuner.enterprise.service.EnterpriseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
@Validated
public class EnterpriseController {

    private final EnterpriseService enterpriseService;

    @GetMapping("/me")
    public ApiResponse<StatusResponse> status(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(enterpriseService.myStatus(authUser.id()));
    }

    @PostMapping("/applications")
    public ApiResponse<ApplicationResponse> apply(@AuthenticationPrincipal AuthUser authUser,
                                                  @RequestBody ApplicationRequest request) {
        return ApiResponse.ok(enterpriseService.apply(authUser.id(), request));
    }

    @GetMapping("/jobs")
    public ApiResponse<List<JobResponse>> myJobs(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(enterpriseService.myJobs(authUser.id()));
    }

    @PostMapping("/jobs")
    public ApiResponse<JobResponse> createJob(@AuthenticationPrincipal AuthUser authUser,
                                              @RequestBody JobRequest request) {
        return ApiResponse.ok(enterpriseService.createJob(authUser.id(), request));
    }

    @PutMapping("/jobs/{jobId}")
    public ApiResponse<JobResponse> updateJob(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long jobId,
                                              @RequestBody JobRequest request) {
        return ApiResponse.ok(enterpriseService.updateJob(authUser.id(), jobId, request));
    }

    @GetMapping("/jobs/public")
    public ApiResponse<List<JobResponse>> publicJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "30") @Min(1) @Max(50) int limit) {
        return ApiResponse.ok(enterpriseService.publicJobs(keyword, limit));
    }
}
