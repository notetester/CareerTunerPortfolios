package com.careertuner.admin.analytics.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.analytics.dto.AdminAnalyticsSummaryResponse;
import com.careertuner.admin.analytics.dto.AdminCareerAnalysisRunResponse;
import com.careertuner.admin.analytics.dto.AdminCareerRunMemoRequest;
import com.careertuner.admin.analytics.dto.AdminCareerRunMemoResponse;
import com.careertuner.admin.analytics.service.AdminAnalyticsService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/summary")
    public ApiResponse<AdminAnalyticsSummaryResponse> summary(@AuthenticationPrincipal AuthUser authUser) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.getSummary());
    }

    @GetMapping("/runs")
    public ApiResponse<List<AdminCareerAnalysisRunResponse>> runs(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) Long userId) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.listRuns(userId));
    }

    // ── 실행 이력 운영 메모(분석 결과 운영 메모). 적합도 메모와 동일 운영 흐름. ──
    @GetMapping("/runs/{runId}/memos")
    public ApiResponse<List<AdminCareerRunMemoResponse>> listMemos(@AuthenticationPrincipal AuthUser authUser,
                                                                   @PathVariable Long runId) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.listMemos(runId));
    }

    @PostMapping("/runs/{runId}/memos")
    public ApiResponse<AdminCareerRunMemoResponse> createMemo(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long runId,
                                                              @Valid @RequestBody AdminCareerRunMemoRequest request) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.createMemo(runId, authUser.id(), request));
    }

    @PatchMapping("/runs/{runId}/memos/{memoId}")
    public ApiResponse<AdminCareerRunMemoResponse> updateMemo(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long runId,
                                                              @PathVariable Long memoId,
                                                              @Valid @RequestBody AdminCareerRunMemoRequest request) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.updateMemo(runId, memoId, request));
    }

    @DeleteMapping("/runs/{runId}/memos/{memoId}")
    public ApiResponse<Void> deleteMemo(@AuthenticationPrincipal AuthUser authUser,
                                        @PathVariable Long runId,
                                        @PathVariable Long memoId) {
        requireAdmin(authUser);
        adminAnalyticsService.deleteMemo(runId, memoId);
        return ApiResponse.ok();
    }

    private static void requireAdmin(AuthUser authUser) {
        if (!"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
    }
}
