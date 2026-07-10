package com.careertuner.admin.analytics.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

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

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.analytics.dto.AdminAnalysisFailureResponse;
import com.careertuner.admin.analytics.dto.AdminAnalyticsSummaryResponse;
import com.careertuner.admin.analytics.dto.AdminCareerAnalysisRunResponse;
import com.careertuner.admin.analytics.dto.AdminCareerRunMemoRequest;
import com.careertuner.admin.analytics.dto.AdminCareerRunMemoResponse;
import com.careertuner.admin.analytics.dto.AdminQualityFlagResponse;
import com.careertuner.admin.analytics.dto.AdminUserTimelineResponse;
import com.careertuner.admin.analytics.service.AdminAnalyticsService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/analytics")
@RequireAdminPermission({"ANALYSIS_READ", "AI_ADMIN"})
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/summary")
    public ApiResponse<AdminAnalyticsSummaryResponse> summary(@AuthenticationPrincipal AuthUser authUser) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.getSummary());
    }

    /** 분석 실패 큐: 적합도/장기/대시보드 분석의 FAILED·FALLBACK 결과 최신순. */
    @GetMapping("/failures")
    public ApiResponse<List<AdminAnalysisFailureResponse>> failures(@AuthenticationPrincipal AuthUser authUser) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.listFailures());
    }

    /** 품질 검수 큐: 최신 적합도 분석에 대한 결정적 휴리스틱 점검 항목. */
    @GetMapping("/quality-flags")
    public ApiResponse<List<AdminQualityFlagResponse>> qualityFlags(@AuthenticationPrincipal AuthUser authUser) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.listQualityFlags());
    }

    @PatchMapping("/quality-flags/{fitAnalysisId}/{flagType}/resolve")
    @RequireAdminPermission({"AI_OPERATION_MANAGE", "AI_ADMIN"})
    public ApiResponse<Void> resolveQualityFlag(@AuthenticationPrincipal AuthUser authUser,
                                                @PathVariable Long fitAnalysisId,
                                                @PathVariable String flagType) {
        requireAdmin(authUser);
        adminAnalyticsService.resolveQualityFlag(fitAnalysisId, flagType);
        return ApiResponse.ok();
    }

    @GetMapping("/runs")
    public ApiResponse<List<AdminCareerAnalysisRunResponse>> runs(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) Long userId) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.listRuns(userId));
    }

    @GetMapping("/users/{userId}/timeline")
    public ApiResponse<List<AdminUserTimelineResponse>> userTimeline(@AuthenticationPrincipal AuthUser authUser,
                                                                     @PathVariable Long userId) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.getUserTimeline(userId));
    }

    // ── 실행 이력 운영 메모(분석 결과 운영 메모). 적합도 메모와 동일 운영 흐름. ──
    @GetMapping("/runs/{runId}/memos")
    public ApiResponse<List<AdminCareerRunMemoResponse>> listMemos(@AuthenticationPrincipal AuthUser authUser,
                                                                   @PathVariable Long runId) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.listMemos(runId));
    }

    @PostMapping("/runs/{runId}/memos")
    @RequireAdminPermission({"AI_OPERATION_MANAGE", "AI_ADMIN"})
    public ApiResponse<AdminCareerRunMemoResponse> createMemo(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long runId,
                                                              @Valid @RequestBody AdminCareerRunMemoRequest request) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.createMemo(runId, authUser.id(), request));
    }

    @PatchMapping("/runs/{runId}/memos/{memoId}")
    @RequireAdminPermission({"AI_OPERATION_MANAGE", "AI_ADMIN"})
    public ApiResponse<AdminCareerRunMemoResponse> updateMemo(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long runId,
                                                              @PathVariable Long memoId,
                                                              @Valid @RequestBody AdminCareerRunMemoRequest request) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.updateMemo(runId, memoId, request));
    }

    @DeleteMapping("/runs/{runId}/memos/{memoId}")
    @RequireAdminPermission({"AI_OPERATION_MANAGE", "AI_ADMIN"})
    public ApiResponse<Void> deleteMemo(@AuthenticationPrincipal AuthUser authUser,
                                        @PathVariable Long runId,
                                        @PathVariable Long memoId) {
        requireAdmin(authUser);
        adminAnalyticsService.deleteMemo(runId, memoId);
        return ApiResponse.ok();
    }

    private static void requireAdmin(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
    }
}
