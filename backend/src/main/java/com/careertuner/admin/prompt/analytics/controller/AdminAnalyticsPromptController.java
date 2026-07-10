package com.careertuner.admin.prompt.analytics.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.prompt.analytics.dto.AdminAnalyticsPromptResponse;
import com.careertuner.admin.prompt.analytics.service.AdminAnalyticsPromptService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/prompts/analytics")
@RequireAdminPermission({"AI_OPERATION_MANAGE", "AI_ADMIN"})
@RequiredArgsConstructor
public class AdminAnalyticsPromptController {

    private final AdminAnalyticsPromptService adminAnalyticsPromptService;

    @GetMapping
    public ApiResponse<List<AdminAnalyticsPromptResponse>> list(@AuthenticationPrincipal AuthUser authUser) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsPromptService.list());
    }

    @GetMapping("/{key}")
    public ApiResponse<AdminAnalyticsPromptResponse> get(@AuthenticationPrincipal AuthUser authUser,
                                                         @PathVariable String key) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsPromptService.get(key));
    }

    private static void requireAdmin(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
    }
}
