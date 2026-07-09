package com.careertuner.admin.settings.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.settings.dto.AdminJobPostingFallbackSettingRequest;
import com.careertuner.admin.settings.dto.AdminJobPostingFallbackSettingResponse;
import com.careertuner.admin.settings.dto.AdminJobPostingUploadLimitSettingRequest;
import com.careertuner.admin.settings.dto.AdminJobPostingUploadLimitSettingResponse;
import com.careertuner.admin.settings.service.AdminAiSettingsService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/ai-settings")
@RequiredArgsConstructor
public class AdminAiSettingsController {

    private final AdminAiSettingsService service;

    @GetMapping("/job-posting-fallback")
    public ApiResponse<AdminJobPostingFallbackSettingResponse> jobPostingFallback(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.jobPostingFallback(authUser));
    }

    @PatchMapping("/job-posting-fallback")
    public ApiResponse<AdminJobPostingFallbackSettingResponse> updateJobPostingFallback(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AdminJobPostingFallbackSettingRequest request) {
        return ApiResponse.ok(service.updateJobPostingFallback(authUser, request));
    }

    @GetMapping("/upload-size")
    public ApiResponse<AdminJobPostingUploadLimitSettingResponse> jobPostingUploadLimit(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.jobPostingUploadLimit(authUser));
    }

    @PatchMapping("/upload-size")
    public ApiResponse<AdminJobPostingUploadLimitSettingResponse> updateJobPostingUploadLimit(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AdminJobPostingUploadLimitSettingRequest request) {
        return ApiResponse.ok(service.updateJobPostingUploadLimit(authUser, request));
    }
}
