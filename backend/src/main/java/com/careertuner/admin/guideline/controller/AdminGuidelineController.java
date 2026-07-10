package com.careertuner.admin.guideline.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.guideline.dto.AdminGuidelineRequest;
import com.careertuner.admin.guideline.dto.AdminGuidelineResponse;
import com.careertuner.admin.guideline.service.AdminGuidelineService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/guidelines")
@RequireAdminPermission({"CONTENT_MANAGE", "CONTENT_ADMIN"})
@RequiredArgsConstructor
public class AdminGuidelineController {

    private final AdminGuidelineService guidelineService;

    @GetMapping
    public ApiResponse<List<AdminGuidelineResponse>> getGuidelines(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(guidelineService.getGuidelines(authUser));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminGuidelineResponse> getGuideline(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.ok(guidelineService.getGuideline(authUser, id));
    }

    @GetMapping("/published")
    public ApiResponse<AdminGuidelineResponse> getPublished(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(guidelineService.getPublished(authUser));
    }

    @PostMapping
    public ApiResponse<AdminGuidelineResponse> createGuideline(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AdminGuidelineRequest request) {
        return ApiResponse.ok(guidelineService.createGuideline(authUser, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminGuidelineResponse> updateGuideline(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestBody AdminGuidelineRequest request) {
        return ApiResponse.ok(guidelineService.updateGuideline(authUser, id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<AdminGuidelineResponse> publishGuideline(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.ok(guidelineService.publishGuideline(authUser, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteGuideline(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        guidelineService.deleteGuideline(authUser, id);
        return ApiResponse.ok();
    }
}
