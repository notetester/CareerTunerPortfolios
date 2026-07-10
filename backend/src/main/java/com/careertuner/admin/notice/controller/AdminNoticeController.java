package com.careertuner.admin.notice.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.notice.dto.AdminNoticeRequest;
import com.careertuner.admin.notice.dto.AdminNoticeResponse;
import com.careertuner.admin.notice.service.AdminNoticeService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/notices")
@RequireAdminPermission({"CONTENT_MANAGE", "CONTENT_ADMIN"})
@RequiredArgsConstructor
public class AdminNoticeController {

    private final AdminNoticeService noticeService;

    @GetMapping
    public ApiResponse<List<AdminNoticeResponse>> getNotices(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(noticeService.getNotices(authUser));
    }

    @PostMapping
    public ApiResponse<AdminNoticeResponse> createNotice(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AdminNoticeRequest request) {
        return ApiResponse.ok(noticeService.createNotice(authUser, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminNoticeResponse> updateNotice(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestBody AdminNoticeRequest request) {
        return ApiResponse.ok(noticeService.updateNotice(authUser, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotice(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        noticeService.deleteNotice(authUser, id);
        return ApiResponse.ok();
    }
}
