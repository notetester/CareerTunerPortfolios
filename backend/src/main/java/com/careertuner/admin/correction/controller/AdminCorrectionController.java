package com.careertuner.admin.correction.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.correction.dto.AdminCorrectionDetail;
import com.careertuner.admin.correction.dto.AdminCorrectionFailureRow;
import com.careertuner.admin.correction.dto.AdminCorrectionMemoRequest;
import com.careertuner.admin.correction.dto.AdminCorrectionPage;
import com.careertuner.admin.correction.dto.AdminCorrectionSummary;
import com.careertuner.admin.correction.service.AdminCorrectionService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/admin/corrections")
@RequireAdminPermission({"ANALYSIS_READ", "AI_ADMIN"})
@RequiredArgsConstructor
public class AdminCorrectionController {

    private final AdminCorrectionService service;

    @GetMapping
    public ApiResponse<AdminCorrectionPage> corrections(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String correctionType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String memoState,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.corrections(
                authUser, keyword, correctionType, status, memoState, page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<AdminCorrectionSummary> summary(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.summary(authUser));
    }

    @GetMapping("/ai-failures")
    public ApiResponse<List<AdminCorrectionFailureRow>> aiFailures(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(service.aiFailures(authUser, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminCorrectionDetail> detail(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(service.detail(authUser, id));
    }

    @PutMapping("/{id}/memo")
    @RequireAdminPermission({"AI_OPERATION_MANAGE", "AI_ADMIN"})
    public ApiResponse<Void> updateMemo(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminCorrectionMemoRequest request
    ) {
        service.updateMemo(authUser, id, request.memo());
        return ApiResponse.ok(null);
    }
}
