package com.careertuner.admin.prompt.fitanalysis.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.prompt.fitanalysis.dto.AdminFitAnalysisPromptResponse;
import com.careertuner.admin.prompt.fitanalysis.service.AdminFitAnalysisPromptService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/prompts/fit-analysis")
@RequiredArgsConstructor
public class AdminFitAnalysisPromptController {

    private final AdminFitAnalysisPromptService adminFitAnalysisPromptService;

    @GetMapping
    public ApiResponse<List<AdminFitAnalysisPromptResponse>> list(@AuthenticationPrincipal AuthUser authUser) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisPromptService.list());
    }

    @GetMapping("/{key}")
    public ApiResponse<AdminFitAnalysisPromptResponse> get(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable String key) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisPromptService.get(key));
    }

    private static void requireAdmin(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
    }
}
