package com.careertuner.notification.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.notification.dto.NotificationPageResponse;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<NotificationPageResponse> getNotifications(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(notificationService.getNotifications(authUser.id(), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Integer> getUnreadCount(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(notificationService.getUnreadCount(authUser.id()));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        notificationService.markAsRead(id, authUser.id());
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        notificationService.markAllAsRead(authUser.id());
        return ApiResponse.ok();
    }
}
