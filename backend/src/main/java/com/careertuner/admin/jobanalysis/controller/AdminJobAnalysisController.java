package com.careertuner.admin.jobanalysis.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisRow;
import com.careertuner.admin.jobanalysis.service.AdminJobAnalysisService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/job-analysis")
@RequiredArgsConstructor
public class AdminJobAnalysisController {

    private final AdminJobAnalysisService service;

    @GetMapping
    public ApiResponse<List<AdminJobAnalysisRow>> jobAnalyses(@AuthenticationPrincipal AuthUser authUser,
                                                              @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.jobAnalyses(authUser, limit));
    }
}
