package com.careertuner.admin.fitanalysis.controller;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.common.grid.PageResult;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisDetailResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListItemResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListQuery;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoRequest;
import com.careertuner.admin.fitanalysis.dto.AdminGateReviewRequest;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoResponse;
import com.careertuner.admin.fitanalysis.dto.AdminGateStatsResponse;
import com.careertuner.admin.fitanalysis.service.AdminFitAnalysisService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/fit-analyses")
@RequireAdminPermission({"ANALYSIS_READ", "AI_ADMIN"})
@RequiredArgsConstructor
public class AdminFitAnalysisController {

    private final AdminFitAnalysisService adminFitAnalysisService;

    @GetMapping
    public ApiResponse<PageResult<AdminFitAnalysisListItemResponse>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(name = "reviewRequiredOnly", required = false, defaultValue = "false") boolean reviewRequiredOnly,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "band", required = false, defaultValue = "ALL") String band,
            @RequestParam(name = "result", required = false, defaultValue = "ALL") String result,
            @RequestParam(name = "memoOnly", required = false, defaultValue = "false") boolean memoOnly,
            @RequestParam(name = "reanalysisOnly", required = false, defaultValue = "false") boolean reanalysisOnly,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size) {
        requireAdmin(authUser);
        AdminFitAnalysisListQuery listQuery = new AdminFitAnalysisListQuery(
                reviewRequiredOnly, query, band, result, memoOnly, reanalysisOnly, page, size);
        return ApiResponse.ok(adminFitAnalysisService.list(listQuery));
    }

    /** gate 통계: 운영 gate reason 분포 관측. 리터럴 경로라 아래 GET /{id} 와 충돌하지 않는다. */
    @GetMapping("/gate-stats")
    public ApiResponse<AdminGateStatsResponse> gateStats(@AuthenticationPrincipal AuthUser authUser) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.getGateStats());
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminFitAnalysisDetailResponse> get(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long id) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.get(id));
    }

    /** gate review workflow: 검토 완료/재분석 요청/대기 되돌리기 처리(+선택 메모). */
    @PatchMapping("/{id}/gate-review")
    @RequireAdminPermission({"AI_OPERATION_MANAGE", "AI_ADMIN"})
    public ApiResponse<AdminFitAnalysisDetailResponse> reviewGate(@AuthenticationPrincipal AuthUser authUser,
                                                                  @PathVariable Long id,
                                                                  @Valid @RequestBody AdminGateReviewRequest request) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.reviewGate(id, authUser.id(), request));
    }

    @GetMapping("/{id}/memos")
    public ApiResponse<List<AdminFitAnalysisMemoResponse>> listMemos(@AuthenticationPrincipal AuthUser authUser,
                                                                     @PathVariable Long id) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.listMemos(id));
    }

    @PostMapping("/{id}/memos")
    @RequireAdminPermission({"AI_OPERATION_MANAGE", "AI_ADMIN"})
    public ApiResponse<AdminFitAnalysisMemoResponse> createMemo(@AuthenticationPrincipal AuthUser authUser,
                                                                @PathVariable Long id,
                                                                @Valid @RequestBody AdminFitAnalysisMemoRequest request) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.createMemo(id, authUser.id(), request));
    }

    @PatchMapping("/{id}/memos/{memoId}")
    @RequireAdminPermission({"AI_OPERATION_MANAGE", "AI_ADMIN"})
    public ApiResponse<AdminFitAnalysisMemoResponse> updateMemo(@AuthenticationPrincipal AuthUser authUser,
                                                                @PathVariable Long id,
                                                                @PathVariable Long memoId,
                                                                @Valid @RequestBody AdminFitAnalysisMemoRequest request) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.updateMemo(id, memoId, request));
    }

    @DeleteMapping("/{id}/memos/{memoId}")
    @RequireAdminPermission({"AI_OPERATION_MANAGE", "AI_ADMIN"})
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
