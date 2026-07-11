package com.careertuner.admin.ops.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.ops.dto.AdminActionLogRow;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/action-logs")
@RequireAdminPermission({"AUDIT_READ"})
@RequiredArgsConstructor
public class AdminActionLogController {

    private final AdminActionLogService service;

    @GetMapping
    public ApiResponse<List<AdminActionLogRow>> logs(@AuthenticationPrincipal AuthUser authUser,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String actionType,
                                                     @RequestParam(required = false) String targetType,
                                                     @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.recent(authUser, keyword, actionType, targetType, limit));
    }
}
