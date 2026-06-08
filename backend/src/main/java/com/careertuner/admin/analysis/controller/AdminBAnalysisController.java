package com.careertuner.admin.analysis.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.analysis.dto.AdminAiUsageLogRow;
import com.careertuner.admin.analysis.dto.AdminCompanyAnalysisRow;
import com.careertuner.admin.analysis.dto.AdminJobAnalysisRow;
import com.careertuner.admin.analysis.service.AdminBAnalysisService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminBAnalysisController {

    private final AdminBAnalysisService service;

    @GetMapping("/job-analysis")
    public ApiResponse<List<AdminJobAnalysisRow>> jobAnalyses(@AuthenticationPrincipal AuthUser authUser,
                                                              @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.jobAnalyses(authUser, limit));
    }

    @GetMapping("/company-analysis")
    public ApiResponse<List<AdminCompanyAnalysisRow>> companyAnalyses(@AuthenticationPrincipal AuthUser authUser,
                                                                      @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.companyAnalyses(authUser, limit));
    }

    @GetMapping("/ai-usage/b")
    public ApiResponse<List<AdminAiUsageLogRow>> usageLogs(@AuthenticationPrincipal AuthUser authUser,
                                                           @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.usageLogs(authUser, limit));
    }
}
