package com.careertuner.dashboard.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.billing.policy.RequiresAiCharge;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.policy.RequiresConsent;
import com.careertuner.dashboard.dto.DashboardDerivedTodoUpdateRequest;
import com.careertuner.dashboard.dto.DashboardSummaryResponse;
import com.careertuner.dashboard.dto.DashboardTodoCreateRequest;
import com.careertuner.dashboard.dto.DashboardTodoResponse;
import com.careertuner.dashboard.dto.DashboardTodoUpdateRequest;
import com.careertuner.dashboard.service.DashboardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @RequiresConsent(ConsentType.AI_DATA)
    public ApiResponse<DashboardSummaryResponse> summary(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(dashboardService.getSummary(authUser.id()));
    }

    // 사용자가 명시적으로 요청한 대시보드 요약 재생성. AI를 강제로 실행하고 크레딧을 차감한다.
    @PostMapping("/summary/refresh")
    @RequiresConsent(ConsentType.AI_DATA)
    @RequiresAiCharge("DASHBOARD_SUMMARY")
    public ApiResponse<DashboardSummaryResponse> refresh(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(dashboardService.refreshSummary(authUser.id()));
    }

    // ----- 오늘의 할 일 (디자인 분석 §6.4: 완료 처리 액션) -----
    // 모든 변경은 갱신된 전체 할 일 목록을 반환해 화면이 즉시 동기화되게 한다. AI는 실행하지 않는다.

    @GetMapping("/todos")
    public ApiResponse<List<DashboardTodoResponse>> todos(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(dashboardService.getTodos(authUser.id()));
    }

    @PostMapping("/todos")
    public ApiResponse<List<DashboardTodoResponse>> addTodo(@AuthenticationPrincipal AuthUser authUser,
                                                            @RequestBody DashboardTodoCreateRequest request) {
        return ApiResponse.ok(dashboardService.addTodo(authUser.id(), request.task(), request.time()));
    }

    /** 파생(자동 계산) 할 일 완료/해제. 경로 충돌을 피하기 위해 /todos/{id}보다 먼저 선언되는 고정 경로를 쓴다. */
    @PatchMapping("/todos/derived")
    public ApiResponse<List<DashboardTodoResponse>> updateDerivedTodo(@AuthenticationPrincipal AuthUser authUser,
                                                                      @RequestBody DashboardDerivedTodoUpdateRequest request) {
        return ApiResponse.ok(dashboardService.updateDerivedTodo(
                authUser.id(), request.derivedKey(), request.done(), request.task(), request.time()));
    }

    @PatchMapping("/todos/{todoId}")
    public ApiResponse<List<DashboardTodoResponse>> updateTodo(@AuthenticationPrincipal AuthUser authUser,
                                                               @PathVariable Long todoId,
                                                               @RequestBody DashboardTodoUpdateRequest request) {
        return ApiResponse.ok(dashboardService.updateUserTodo(authUser.id(), todoId, request.done()));
    }

    @DeleteMapping("/todos/{todoId}")
    public ApiResponse<List<DashboardTodoResponse>> deleteTodo(@AuthenticationPrincipal AuthUser authUser,
                                                               @PathVariable Long todoId) {
        return ApiResponse.ok(dashboardService.deleteTodo(authUser.id(), todoId));
    }
}
