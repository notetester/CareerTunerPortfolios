package com.careertuner.admin.community.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.community.dto.AdminReportActionRequest;
import com.careertuner.admin.community.dto.AdminReportDetailResponse;
import com.careertuner.admin.community.dto.AdminReportListResponse;
import com.careertuner.admin.community.service.AdminReportService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 신고 콘솔. 세부 권한: 콘텐츠/고객지원 관리(CONTENT_MANAGE) 또는 대표 권한(CONTENT_ADMIN). */
@RestController
@RequestMapping("/api/admin/community/reports")
@RequiredArgsConstructor
@RequireAdminPermission({"CONTENT_MANAGE", "CONTENT_ADMIN"})
public class AdminReportController {

    private final AdminReportService reportService;

    @GetMapping
    public ApiResponse<List<AdminReportListResponse>> getReports(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(reportService.getReports(authUser, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminReportDetailResponse> getReportDetail(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.ok(reportService.getReportDetail(authUser, id));
    }

    @PostMapping("/{id}/action")
    public ApiResponse<AdminReportDetailResponse> takeAction(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestBody AdminReportActionRequest request) {
        return ApiResponse.ok(reportService.takeAction(authUser, id, request));
    }

    /** 종결(기각/취소) 신고 재활성화 — PENDING 복원. */
    @PostMapping("/{id}/reactivate")
    public ApiResponse<AdminReportDetailResponse> reactivate(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.ok(reportService.reactivate(authUser, id));
    }

    @PostMapping("/{id}/reclassify")
    public ApiResponse<AdminReportDetailResponse> reclassify(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.ok(reportService.reclassify(authUser, id));
    }
}
