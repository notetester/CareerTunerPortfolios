package com.careertuner.admin.ads.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.ads.dto.AdminAdRequest;
import com.careertuner.admin.ads.dto.AdminAdResponse;
import com.careertuner.admin.ads.service.AdminAdService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 광고 관리 콘솔. 세부 권한: 콘텐츠 관리(CONTENT_MANAGE) 또는 대표 권한(CONTENT_ADMIN).
 * 노출/클릭 통계는 응답 DTO 의 impressionCount/clickCount/ctr 로 함께 제공한다.
 */
@RestController
@RequestMapping("/api/admin/ads")
@RequiredArgsConstructor
@RequireAdminPermission({"CONTENT_MANAGE", "CONTENT_ADMIN"})
public class AdminAdController {

    private final AdminAdService adminAdService;

    @GetMapping
    public ApiResponse<List<AdminAdResponse>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String placement,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(adminAdService.list(authUser, placement, platform, activeOnly));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminAdResponse> get(@AuthenticationPrincipal AuthUser authUser,
                                            @PathVariable Long id) {
        return ApiResponse.ok(adminAdService.get(authUser, id));
    }

    @PostMapping
    public ApiResponse<AdminAdResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                               @Valid @RequestBody AdminAdRequest request) {
        return ApiResponse.ok(adminAdService.create(authUser, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminAdResponse> update(@AuthenticationPrincipal AuthUser authUser,
                                               @PathVariable Long id,
                                               @Valid @RequestBody AdminAdRequest request) {
        return ApiResponse.ok(adminAdService.update(authUser, id, request));
    }

    @PostMapping("/{id}/toggle-active")
    public ApiResponse<AdminAdResponse> toggleActive(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long id,
                                                     @RequestParam boolean active) {
        return ApiResponse.ok(adminAdService.toggleActive(authUser, id, active));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long id) {
        adminAdService.delete(authUser, id);
        return ApiResponse.ok();
    }
}
