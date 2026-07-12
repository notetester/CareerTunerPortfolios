package com.careertuner.notification.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.notification.dto.NotificationPageResponse;
import com.careertuner.notification.dto.NotificationPreferenceResponse;
import com.careertuner.notification.dto.NotificationPreferenceUpdateRequest;
import com.careertuner.notification.dto.PushSubscribeRequest;
import com.careertuner.notification.domain.NotificationDestinationPlatform;
import com.careertuner.notification.push.PushService;
import com.careertuner.notification.service.NotificationPreferenceService;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final PushService pushService;

    @GetMapping
    public ApiResponse<NotificationPageResponse> getNotifications(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) NotificationDestinationPlatform platform,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(notificationService.getNotifications(authUser.id(), page, size, platform));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Integer> getUnreadCount(
            @RequestParam(required = false) NotificationDestinationPlatform platform,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(notificationService.getUnreadCount(authUser.id(), platform));
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
            @RequestParam(required = false) NotificationDestinationPlatform platform,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        notificationService.markAllAsRead(authUser.id(), platform);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        notificationService.delete(id, authUser.id());
        return ApiResponse.ok();
    }

    @DeleteMapping
    public ApiResponse<Void> deleteAll(
            @RequestParam(required = false) NotificationDestinationPlatform platform,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        notificationService.deleteAll(authUser.id(), platform);
        return ApiResponse.ok();
    }

    // ───── 알림 설정 ─────

    @GetMapping("/preferences")
    public ApiResponse<NotificationPreferenceResponse> getPreferences(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(preferenceService.get(authUser.id()));
    }

    @PutMapping("/preferences")
    public ApiResponse<NotificationPreferenceResponse> updatePreferences(
            @AuthenticationPrincipal AuthUser authUser,
            @Validated @RequestBody NotificationPreferenceUpdateRequest request) {
        return ApiResponse.ok(preferenceService.update(authUser.id(), request));
    }

    // ───── 푸시 기기 등록 ─────

    @PostMapping("/push")
    public ApiResponse<Void> subscribePush(
            @AuthenticationPrincipal AuthUser authUser,
            @Validated @RequestBody PushSubscribeRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        pushService.subscribe(authUser.id(), request, userAgent);
        return ApiResponse.ok();
    }

    @DeleteMapping("/push")
    public ApiResponse<Void> unsubscribePush(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam String token) {
        pushService.unsubscribe(authUser.id(), token);
        return ApiResponse.ok();
    }
}
