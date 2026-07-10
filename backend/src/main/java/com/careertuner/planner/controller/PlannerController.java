package com.careertuner.planner.controller;

import java.time.LocalDateTime;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.policy.RequiresConsent;
import com.careertuner.planner.dto.PlannerDashboardResponse;
import com.careertuner.planner.dto.PlannerMemoRequest;
import com.careertuner.planner.dto.PlannerMemoResponse;
import com.careertuner.planner.dto.PlannerScheduleItemRequest;
import com.careertuner.planner.dto.PlannerScheduleItemResponse;
import com.careertuner.planner.dto.PlannerStrategyDraftResponse;
import com.careertuner.planner.service.PlannerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/planner")
@RequiredArgsConstructor
public class PlannerController {

    private final PlannerService plannerService;

    @GetMapping
    public ApiResponse<PlannerDashboardResponse> dashboard(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ApiResponse.ok(plannerService.getDashboard(authUser.id(), from, to));
    }

    @PostMapping("/memos")
    public ApiResponse<PlannerMemoResponse> createMemo(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody PlannerMemoRequest request) {
        return ApiResponse.ok(plannerService.createMemo(authUser.id(), request));
    }

    @PutMapping("/memos/{memoId}")
    public ApiResponse<PlannerMemoResponse> updateMemo(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long memoId,
            @Valid @RequestBody PlannerMemoRequest request) {
        return ApiResponse.ok(plannerService.updateMemo(authUser.id(), memoId, request));
    }

    @DeleteMapping("/memos/{memoId}")
    public ApiResponse<Void> deleteMemo(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long memoId) {
        plannerService.deleteMemo(authUser.id(), memoId);
        return ApiResponse.ok();
    }

    @PostMapping("/schedule")
    public ApiResponse<PlannerScheduleItemResponse> createScheduleItem(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody PlannerScheduleItemRequest request) {
        return ApiResponse.ok(plannerService.createScheduleItem(authUser.id(), request));
    }

    @PutMapping("/schedule/{itemId}")
    public ApiResponse<PlannerScheduleItemResponse> updateScheduleItem(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long itemId,
            @Valid @RequestBody PlannerScheduleItemRequest request) {
        return ApiResponse.ok(plannerService.updateScheduleItem(authUser.id(), itemId, request));
    }

    @DeleteMapping("/schedule/{itemId}")
    public ApiResponse<Void> deleteScheduleItem(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long itemId) {
        plannerService.deleteScheduleItem(authUser.id(), itemId);
        return ApiResponse.ok();
    }

    @PostMapping("/strategy-drafts/fit-analyses/{fitAnalysisId}")
    @RequiresConsent(ConsentType.AI_DATA)
    public ApiResponse<PlannerStrategyDraftResponse> createStrategyDraft(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long fitAnalysisId) {
        return ApiResponse.ok(plannerService.createStrategyDraft(authUser.id(), fitAnalysisId));
    }
}
