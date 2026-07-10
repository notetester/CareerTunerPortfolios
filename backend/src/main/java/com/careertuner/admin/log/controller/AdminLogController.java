package com.careertuner.admin.log.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.log.dto.AdminAiUsageLogEntry;
import com.careertuner.admin.log.service.AdminLogService;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 시스템 로그(현재는 AI 사용 로그). /api/admin/** = ADMIN 정책으로 보호. */
@RestController
@RequestMapping("/api/admin/logs")
@RequireAdminPermission({"ADMIN_AUDIT_READ", "AUDIT_ADMIN"})
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminLogService adminLogService;

    @GetMapping("/ai-usage")
    public ApiResponse<List<AdminAiUsageLogEntry>> aiUsage(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(adminLogService.getRecentAiUsage(status, limit));
    }
}
