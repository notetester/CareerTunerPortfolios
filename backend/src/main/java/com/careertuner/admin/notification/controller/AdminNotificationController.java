package com.careertuner.admin.notification.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.notification.dto.AdminCampaignRequest;
import com.careertuner.admin.notification.dto.AdminCampaignResponse;
import com.careertuner.admin.notification.dto.AdminNotificationResponse;
import com.careertuner.admin.notification.dto.AdminNotificationStatsResponse;
import com.careertuner.admin.notification.service.AdminCampaignService;
import com.careertuner.admin.notification.service.AdminNotificationService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 알림 모니터링/캠페인 발송 콘솔. 조회와 캠페인 생성을 분리해 집행한다. */
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@RequireAdminPermission({"CONTENT_READ"})
public class AdminNotificationController {

    private final AdminNotificationService notificationService;
    private final AdminCampaignService campaignService;

    @GetMapping
    public ApiResponse<List<AdminNotificationResponse>> getNotifications(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "100") int size) {
        return ApiResponse.ok(notificationService.getNotifications(authUser, size));
    }

    @GetMapping("/stats")
    public ApiResponse<AdminNotificationStatsResponse> getStats(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(notificationService.getStats(authUser));
    }

    /** 캠페인(공지/광고/추천) 알림을 ACTIVE 사용자 전원에게 발송한다. */
    @PostMapping("/campaign")
    @RequireAdminPermission({"CONTENT_CREATE"})
    public ApiResponse<AdminCampaignResponse> sendCampaign(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AdminCampaignRequest request) {
        return ApiResponse.ok(campaignService.sendCampaign(authUser, request));
    }
}
