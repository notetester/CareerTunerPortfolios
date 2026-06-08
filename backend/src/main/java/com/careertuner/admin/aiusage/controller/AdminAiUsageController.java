package com.careertuner.admin.aiusage.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.aiusage.dto.AdminAiUsageLogRow;
import com.careertuner.admin.aiusage.service.AdminAiUsageService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/ai-usage")
@RequiredArgsConstructor
public class AdminAiUsageController {

    private final AdminAiUsageService service;

    @GetMapping("/b")
    public ApiResponse<List<AdminAiUsageLogRow>> usageLogs(@AuthenticationPrincipal AuthUser authUser,
                                                           @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.bUsageLogs(authUser, limit));
    }
}
