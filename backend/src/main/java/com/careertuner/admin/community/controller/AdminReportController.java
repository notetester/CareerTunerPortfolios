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
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/community/reports")
@RequiredArgsConstructor
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

    @PostMapping("/{id}/reclassify")
    public ApiResponse<AdminReportDetailResponse> reclassify(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.ok(reportService.reclassify(authUser, id));
    }
}
