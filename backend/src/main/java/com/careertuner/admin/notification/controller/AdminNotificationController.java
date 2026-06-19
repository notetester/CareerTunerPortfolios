package com.careertuner.admin.notification.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.notification.dto.AdminNotificationResponse;
import com.careertuner.admin.notification.service.AdminNotificationService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService notificationService;

    @GetMapping
    public ApiResponse<List<AdminNotificationResponse>> getNotifications(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "100") int size) {
        return ApiResponse.ok(notificationService.getNotifications(authUser, size));
    }
}
