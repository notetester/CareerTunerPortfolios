package com.careertuner.admin.aiusage.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.aiusage.dto.AdminAiUsageLogRow;
import com.careertuner.admin.aiusage.dto.AdminAiUsageSearchCriteria;
import com.careertuner.admin.aiusage.dto.AdminAiUsageSummary;
import com.careertuner.admin.aiusage.service.AdminAiUsageService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/ai-usage")
@RequireAdminPermission({"AI_READ"})
@RequiredArgsConstructor
public class AdminAiUsageController {

    private final AdminAiUsageService service;

    @GetMapping("/b")
    public ApiResponse<List<AdminAiUsageLogRow>> usageLogs(@AuthenticationPrincipal AuthUser authUser,
                                                           @RequestParam(required = false) String featureType,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Long applicationCaseId,
                                                           @RequestParam(required = false) Long userId,
                                                           @RequestParam(required = false) String model,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                                           @RequestParam(required = false) String sort,
                                                           @RequestParam(defaultValue = "50") int limit,
                                                           @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(service.bUsageLogs(authUser, AdminAiUsageSearchCriteria.builder()
                .featureType(featureType)
                .status(status)
                .keyword(keyword)
                .applicationCaseId(applicationCaseId)
                .userId(userId)
                .model(model)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .sort(sort)
                .limit(limit)
                .offset(offset)
                .build()));
    }

    @GetMapping("/b/summary")
    public ApiResponse<AdminAiUsageSummary> bUsageSummary(@AuthenticationPrincipal AuthUser authUser,
                                                          @RequestParam(required = false) String featureType,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Long applicationCaseId,
                                                          @RequestParam(required = false) Long userId,
                                                          @RequestParam(required = false) String model,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo) {
        return ApiResponse.ok(service.bUsageSummary(authUser, AdminAiUsageSearchCriteria.builder()
                .featureType(featureType)
                .status(status)
                .keyword(keyword)
                .applicationCaseId(applicationCaseId)
                .userId(userId)
                .model(model)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .build()));
    }
}
