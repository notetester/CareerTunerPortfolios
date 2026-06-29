package com.careertuner.admin.fitanalysis.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisDetailResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListItemResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoRequest;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoResponse;
import com.careertuner.admin.fitanalysis.service.AdminFitAnalysisService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/fit-analyses")
@RequiredArgsConstructor
public class AdminFitAnalysisController {

    private final AdminFitAnalysisService adminFitAnalysisService;

    @GetMapping
    public ApiResponse<List<AdminFitAnalysisListItemResponse>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(name = "reviewRequiredOnly", required = false, defaultValue = "false") boolean reviewRequiredOnly) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.list(reviewRequiredOnly));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminFitAnalysisDetailResponse> get(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long id) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.get(id));
    }

    @GetMapping("/{id}/memos")
    public ApiResponse<List<AdminFitAnalysisMemoResponse>> listMemos(@AuthenticationPrincipal AuthUser authUser,
                                                                     @PathVariable Long id) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.listMemos(id));
    }

    @PostMapping("/{id}/memos")
    public ApiResponse<AdminFitAnalysisMemoResponse> createMemo(@AuthenticationPrincipal AuthUser authUser,
                                                                @PathVariable Long id,
                                                                @Valid @RequestBody AdminFitAnalysisMemoRequest request) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.createMemo(id, authUser.id(), request));
    }

    @PatchMapping("/{id}/memos/{memoId}")
    public ApiResponse<AdminFitAnalysisMemoResponse> updateMemo(@AuthenticationPrincipal AuthUser authUser,
                                                                @PathVariable Long id,
                                                                @PathVariable Long memoId,
                                                                @Valid @RequestBody AdminFitAnalysisMemoRequest request) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.updateMemo(id, memoId, request));
    }

    @DeleteMapping("/{id}/memos/{memoId}")
    public ApiResponse<Void> deleteMemo(@AuthenticationPrincipal AuthUser authUser,
                                        @PathVariable Long id,
                                        @PathVariable Long memoId) {
        requireAdmin(authUser);
        adminFitAnalysisService.deleteMemo(id, memoId);
        return ApiResponse.ok();
    }

    private static void requireAdmin(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
    }
}
