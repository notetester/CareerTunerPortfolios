package com.careertuner.admin.user.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.user.dto.AdminUserDetail;
import com.careertuner.admin.user.dto.AdminUserLoginHistoryRow;
import com.careertuner.admin.user.dto.AdminUserRow;
import com.careertuner.admin.user.dto.AdminUserStatusUpdateRequest;
import com.careertuner.admin.user.service.AdminUserService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService service;

    @GetMapping
    public ApiResponse<List<AdminUserRow>> users(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.users(authUser, keyword, status, role, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetail> detail(@AuthenticationPrincipal AuthUser authUser,
                                               @PathVariable Long id) {
        return ApiResponse.ok(service.detail(authUser, id));
    }

    @GetMapping("/{id}/login-history")
    public ApiResponse<List<AdminUserLoginHistoryRow>> loginHistory(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.loginHistory(authUser, id, limit));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminUserRow> updateStatus(@AuthenticationPrincipal AuthUser authUser,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        return ApiResponse.ok(service.updateStatus(authUser, id, request));
    }
}
