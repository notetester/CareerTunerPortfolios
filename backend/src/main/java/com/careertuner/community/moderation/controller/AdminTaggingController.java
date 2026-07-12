package com.careertuner.community.moderation.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.moderation.service.AdminTaggingService;
import com.careertuner.community.moderation.service.AdminTaggingService.BatchStatus;

/**
 * AI 태깅 관리자 엔드포인트.
 *
 * POST /api/admin/ai/tagging/backfill        — 배치 태깅 (dryRun, force 옵션)
 * POST /api/admin/ai/tagging/{postId}        — 단건 재태깅 (force 옵션)
 * GET  /api/admin/ai/tagging/status          — 배치 진행 상태
 */
@RestController
@RequestMapping("/api/admin/ai/tagging")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequireAdminPermission({"AI_READ"})
public class AdminTaggingController {

    private final AdminTaggingService taggingService;

    public AdminTaggingController(AdminTaggingService taggingService) {
        this.taggingService = taggingService;
    }

    /**
     * 배치 태깅.
     * ?dryRun=true → 대상 건수만 반환 (실제 태깅 안 함)
     * ?force=true  → 이미 COMPLETED인 글도 재태깅
     */
    @PostMapping("/backfill")
    @RequireAdminPermission({"AI_CREATE"})
    public ApiResponse<Map<String, Object>> backfill(
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        if (dryRun) {
            int count = taggingService.countTargets(force);
            return ApiResponse.ok(Map.of(
                    "dryRun", true,
                    "targetCount", count,
                    "message", count + "건이 태깅 대상입니다."
            ));
        }

        int total = taggingService.startBackfill(force);
        if (total == 0) {
            return ApiResponse.ok(Map.of(
                    "message", "태깅 대상 게시글이 없습니다.",
                    "targetCount", 0
            ));
        }

        return ApiResponse.ok(Map.of(
                "message", total + "건 배치 태깅을 시작했습니다. GET /status로 진행 상태를 확인하세요.",
                "targetCount", total
        ));
    }

    /**
     * 단건 재태깅 (동기 실행).
     * ?force=true → 이미 COMPLETED인 글도 재태깅
     */
    @PostMapping("/{postId}")
    @RequireAdminPermission({"AI_CREATE"})
    public ApiResponse<Map<String, Object>> tagSingle(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        taggingService.tagSingle(postId, force);
        return ApiResponse.ok(Map.of(
                "postId", postId,
                "message", "태깅이 완료되었습니다."
        ));
    }

    /**
     * 배치 진행 상태 조회.
     */
    @GetMapping("/status")
    public ApiResponse<BatchStatus> status() {
        return ApiResponse.ok(taggingService.getStatus());
    }
}
