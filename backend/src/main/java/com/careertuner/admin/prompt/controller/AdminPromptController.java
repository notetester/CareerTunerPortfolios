package com.careertuner.admin.prompt.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.prompt.dto.AdminPromptView;
import com.careertuner.admin.prompt.service.AdminPromptService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/prompts")
@RequiredArgsConstructor
public class AdminPromptController {

    private final AdminPromptService service;

    @GetMapping("/job-analysis")
    public ApiResponse<AdminPromptView> jobAnalysis(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.jobAnalysis(authUser));
    }

    @GetMapping("/company-analysis")
    public ApiResponse<AdminPromptView> companyAnalysis(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.companyAnalysis(authUser));
    }

    @GetMapping("/profile")
    public ApiResponse<AdminPromptView> profile(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.profile(authUser));
    }
}
