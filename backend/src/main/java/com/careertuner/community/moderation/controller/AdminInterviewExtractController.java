package com.careertuner.community.moderation.controller;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.moderation.service.AdminInterviewExtractService;
import com.careertuner.community.moderation.service.AdminInterviewExtractService.BatchStatus;

/**
 * 면접 질문 AI 추출 관리자 엔드포인트.
 *
 * POST /api/admin/ai/interview-extract/backfill  — 배치 추출 (dryRun, force 옵션)
 * POST /api/admin/ai/interview-extract/{postId}  — 단건 재추출 (force 옵션)
 * GET  /api/admin/ai/interview-extract/status    — 배치 진행 상태
 */
@RestController
@RequestMapping("/api/admin/ai/interview-extract")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminInterviewExtractController {

    private final AdminInterviewExtractService extractService;

    public AdminInterviewExtractController(AdminInterviewExtractService extractService) {
        this.extractService = extractService;
    }

    /**
     * 배치 면접 질문 추출.
     * ?dryRun=true → 대상 건수만 반환 (실제 추출 안 함)
     * ?force=true  → 이미 COMPLETED인 글도 재추출
     */
    @PostMapping("/backfill")
    public ApiResponse<Map<String, Object>> backfill(
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        if (dryRun) {
            int count = extractService.countTargets(force);
            return ApiResponse.ok(Map.of(
                    "dryRun", true,
                    "targetCount", count,
                    "message", count + "건이 면접 질문 추출 대상입니다."
            ));
        }

        int total = extractService.startBackfill(force);
        if (total == 0) {
            return ApiResponse.ok(Map.of(
                    "message", "추출 대상 면접후기 게시글이 없습니다.",
                    "targetCount", 0
            ));
        }

        return ApiResponse.ok(Map.of(
                "message", total + "건 배치 면접 질문 추출을 시작했습니다. GET /status로 진행 상태를 확인하세요.",
                "targetCount", total
        ));
    }

    /**
     * 단건 재추출 (동기 실행).
     * ?force=true → 이미 COMPLETED인 글도 재추출
     */
    @PostMapping("/{postId}")
    public ApiResponse<Map<String, Object>> extractSingle(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        extractService.extractSingle(postId, force);
        return ApiResponse.ok(Map.of(
                "postId", postId,
                "message", "면접 질문 추출이 완료되었습니다."
        ));
    }

    /**
     * 배치 진행 상태 조회.
     */
    @GetMapping("/status")
    public ApiResponse<BatchStatus> status() {
        return ApiResponse.ok(extractService.getStatus());
    }
}
