package com.careertuner.admin.pending.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.pending.dto.AdminPendingCountsResponse;
import com.careertuner.admin.pending.service.AdminPendingCountService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 사이드바 미처리 큐 카운트(신고/자동숨김/티켓) + 색 판정.
 * 프론트 사이드바가 30초 폴링으로 뱃지를 갱신한다.
 */
@RestController
@RequestMapping("/api/admin/pending-counts")
@RequiredArgsConstructor
public class AdminPendingCountController {

    private final AdminPendingCountService adminPendingCountService;

    @GetMapping
    public ApiResponse<AdminPendingCountsResponse> pendingCounts(@AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(adminPendingCountService.getPendingCounts());
    }
}
