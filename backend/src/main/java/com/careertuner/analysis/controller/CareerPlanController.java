package com.careertuner.analysis.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.analysis.dto.CareerGoalRequest;
import com.careertuner.analysis.dto.CareerGoalResponse;
import com.careertuner.analysis.dto.CareerPlanResponse;
import com.careertuner.analysis.dto.LearningPlanRequest;
import com.careertuner.analysis.dto.LearningPlanResponse;
import com.careertuner.analysis.dto.LearningPlanTaskRequest;
import com.careertuner.analysis.dto.LearningPlanTaskResponse;
import com.careertuner.analysis.dto.LearningPlanTaskUpdateRequest;
import com.careertuner.analysis.service.CareerPlanService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/analysis/plan")
@RequiredArgsConstructor
public class CareerPlanController {

    private final CareerPlanService service;

    @GetMapping
    public ApiResponse<CareerPlanResponse> get(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.get(authUser.id()));
    }

    @PutMapping("/goal")
    public ApiResponse<CareerGoalResponse> updateGoal(@AuthenticationPrincipal AuthUser authUser,
                                                       @Valid @RequestBody CareerGoalRequest request) {
        return ApiResponse.ok(service.updateGoal(authUser.id(), request));
    }

    @PostMapping("/learning-plans")
    public ApiResponse<LearningPlanResponse> createPlan(@AuthenticationPrincipal AuthUser authUser,
                                                         @Valid @RequestBody LearningPlanRequest request) {
        return ApiResponse.ok(service.createPlan(authUser.id(), request));
    }

    @PostMapping("/learning-plans/{planId}/tasks")
    public ApiResponse<LearningPlanTaskResponse> createTask(@AuthenticationPrincipal AuthUser authUser,
                                                            @PathVariable Long planId,
                                                            @Valid @RequestBody LearningPlanTaskRequest request) {
        return ApiResponse.ok(service.createTask(authUser.id(), planId, request));
    }

    @PatchMapping("/learning-plans/{planId}/tasks/{taskId}")
    public ApiResponse<LearningPlanTaskResponse> updateTask(@AuthenticationPrincipal AuthUser authUser,
                                                            @PathVariable Long planId,
                                                            @PathVariable Long taskId,
                                                            @RequestBody LearningPlanTaskUpdateRequest request) {
        return ApiResponse.ok(service.updateTask(authUser.id(), planId, taskId, request.done()));
    }
}
