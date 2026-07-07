package com.careertuner.community.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.dto.ActivityPageResponse;
import com.careertuner.community.service.CommunityActivityService;

import lombok.RequiredArgsConstructor;

/**
 * 활동 목록 — 내 활동(글/댓글/답글/좋아요/즐겨찾기/스크랩)과 타인 프로필 활동 탭.
 * 타인 조회는 PrivacyPolicyService.allows(대상, 뷰어, "activity.{tab}") 로
 * 차단·관계별 공개범위를 자동 검사하고, 익명 작성/익명 리액션 항목은 제외한다.
 */
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityActivityController {

    private final CommunityActivityService activityService;

    @GetMapping("/me/activity")
    public ApiResponse<ActivityPageResponse> getMyActivity(
            @RequestParam(defaultValue = "posts") String tab,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(activityService.getMyActivity(authUser.id(), tab, page, size));
    }

    @GetMapping("/users/{userId}/activity")
    public ApiResponse<ActivityPageResponse> getUserActivity(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "posts") String tab,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthUser authUser) {
        Long viewerId = authUser != null ? authUser.id() : null;
        return ApiResponse.ok(activityService.getUserActivity(userId, viewerId, tab, page, size));
    }

    /** 프로필 활동 탭 헤더 — 탭별 공개 여부(비공개 탭은 잠금 표시) + 표시명. */
    @GetMapping("/users/{userId}/activity-tabs")
    public ApiResponse<ActivityPageResponse.TabsDto> getUserActivityTabs(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthUser authUser) {
        Long viewerId = authUser != null ? authUser.id() : null;
        return ApiResponse.ok(activityService.getUserActivityTabs(userId, viewerId));
    }
}
