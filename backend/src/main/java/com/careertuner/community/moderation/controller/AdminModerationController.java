package com.careertuner.community.moderation.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.web.ApiResponse;
import com.careertuner.common.security.AuthUser;
import com.careertuner.community.moderation.domain.ModerationSetting;
import com.careertuner.community.moderation.domain.Strictness;
import com.careertuner.community.moderation.dto.ModerationDetailResponse;
import com.careertuner.community.moderation.dto.ModerationListRequest;
import com.careertuner.community.moderation.dto.ModerationPageResponse;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.dto.ModerationReviewDecisionRequest;
import com.careertuner.community.moderation.dto.ModerationReviewQueuePageResponse;
import com.careertuner.community.moderation.dto.ModerationSettingResponse;
import com.careertuner.community.moderation.dto.ModerationSettingUpdateRequest;
import com.careertuner.community.moderation.dto.ModerationStatsResponse;
import com.careertuner.community.moderation.dto.ModerationTestRequest;
import com.careertuner.community.moderation.dto.ModerationTestResponse;
import java.util.Map;

import com.careertuner.community.moderation.service.AdminModerationBackfillService;
import com.careertuner.community.moderation.service.AdminModerationBackfillService.BatchStatus;
import com.careertuner.community.moderation.service.AdminModerationService;
import com.careertuner.community.moderation.service.ModerationSettingService;
import com.careertuner.community.moderation.service.PostModerationService;

/**
 * AI 검열 관리자 엔드포인트.
 */
@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequireAdminPermission({"AI_READ"})
public class AdminModerationController {

    private final PostModerationService moderationService;
    private final AdminModerationService adminModerationService;
    private final AdminModerationBackfillService backfillService;
    private final ModerationSettingService settingService;

    public AdminModerationController(PostModerationService moderationService,
                                     AdminModerationService adminModerationService,
                                     AdminModerationBackfillService backfillService,
                                     ModerationSettingService settingService) {
        this.moderationService = moderationService;
        this.adminModerationService = adminModerationService;
        this.backfillService = backfillService;
        this.settingService = settingService;
    }

    /**
     * 배치 검열.
     * SQL 직접 INSERT 등으로 작성 이벤트가 누락된 글을 사후 일괄 검열한다.
     * ?dryRun=true → 대상 건수만 반환 (실제 검열 안 함)
     * ?force=true  → 이미 COMPLETED인 글도 재검열
     */
    @PostMapping("/moderation/backfill")
    @RequireAdminPermission({"AI_CREATE"})
    public ApiResponse<Map<String, Object>> backfill(
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        if (dryRun) {
            int count = backfillService.countTargets(force);
            return ApiResponse.ok(Map.of(
                    "dryRun", true,
                    "targetCount", count,
                    "message", count + "건이 검열 대상입니다."
            ));
        }

        int total = backfillService.startBackfill(force);
        if (total == 0) {
            return ApiResponse.ok(Map.of(
                    "message", "검열 대상 게시글이 없습니다.",
                    "targetCount", 0
            ));
        }

        return ApiResponse.ok(Map.of(
                "message", total + "건 배치 검열을 시작했습니다. GET /moderation/backfill/status로 진행 상태를 확인하세요.",
                "targetCount", total
        ));
    }

    /**
     * 배치 검열 진행 상태 조회.
     */
    @GetMapping("/moderation/backfill/status")
    public ApiResponse<BatchStatus> backfillStatus() {
        return ApiResponse.ok(backfillService.getStatus());
    }

    /**
     * 단건 재검열 (동기 실행).
     * ?force=true → 이미 COMPLETED인 글도 재검열
     */
    @PostMapping("/moderation/{postId}/run")
    @RequireAdminPermission({"AI_CREATE"})
    public ApiResponse<Map<String, Object>> moderateSingle(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        backfillService.moderateSingle(postId, force);
        return ApiResponse.ok(Map.of(
                "postId", postId,
                "message", "검열이 완료되었습니다."
        ));
    }

    /** 검열 테스트 (DB 기록 없이 judge()만 호출) */
    @PostMapping("/moderation-test")
    @RequireAdminPermission({"AI_CREATE"})
    public ApiResponse<ModerationTestResponse> test(
            @Validated @RequestBody ModerationTestRequest request
    ) {
        long start = System.currentTimeMillis();
        ModerationResult result = moderationService.judge(
                request.title() != null ? request.title() : "",
                request.content()
        );
        long elapsed = System.currentTimeMillis() - start;

        return ApiResponse.ok(new ModerationTestResponse(
                result.toxic(),
                result.category(),
                result.confidence(),
                elapsed
        ));
    }

    /** 검열 결과 목록 */
    @GetMapping("/moderation")
    public ApiResponse<ModerationPageResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean toxic,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ModerationListRequest request = new ModerationListRequest(status, toxic, page, size);
        return ApiResponse.ok(adminModerationService.getList(request));
    }

    /** 검열 단건 상세 */
    @GetMapping("/moderation/{postId}")
    public ApiResponse<ModerationDetailResponse> detail(@PathVariable Long postId) {
        return ApiResponse.ok(adminModerationService.getDetail(postId));
    }

    /** toxic이지만 현재 자동 숨김 임계 미만인 게시 중 글의 수동 검토 큐 */
    @GetMapping("/moderation/review-queue")
    public ApiResponse<ModerationReviewQueuePageResponse> reviewQueue(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(adminModerationService.getReviewQueue(page, size));
    }

    /** 수동 검토 결정. HIDE는 PUBLISHED→HIDDEN, KEEP은 게시 상태를 유지한다. */
    @PatchMapping("/moderation/review-queue/{postId}")
    @RequireAdminPermission({"CONTENT_UPDATE"})
    public ApiResponse<Void> decideReviewQueue(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long postId,
            @RequestBody ModerationReviewDecisionRequest request
    ) {
        adminModerationService.decideReviewQueue(authUser.id(), postId, request.action());
        return ApiResponse.ok(null);
    }

    /** HIDDEN → PUBLISHED 복원 */
    @PostMapping("/moderation/{postId}/restore")
    @RequireAdminPermission({"CONTENT_UPDATE"})
    public ApiResponse<Void> restore(@PathVariable Long postId) {
        adminModerationService.restore(postId);
        return ApiResponse.ok(null);
    }

    /** HIDDEN → DELETED 확정 삭제 */
    @PostMapping("/moderation/{postId}/delete")
    @RequireAdminPermission({"CONTENT_DELETE"})
    public ApiResponse<Void> delete(@PathVariable Long postId) {
        adminModerationService.delete(postId);
        return ApiResponse.ok(null);
    }

    /** AI 판정 카테고리별 통계 */
    @GetMapping("/moderation/stats")
    public ApiResponse<ModerationStatsResponse> stats() {
        return ApiResponse.ok(adminModerationService.getStats());
    }

    // ── 댓글 검열 (게시글 엔드포인트 복제, 경로만 /comments). "comments" 리터럴이 {postId}보다 우선 매칭됨 ──

    /** 댓글 검열 결과 목록 */
    @GetMapping("/moderation/comments")
    public ApiResponse<ModerationPageResponse> commentList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean toxic,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ModerationListRequest request = new ModerationListRequest(status, toxic, page, size);
        return ApiResponse.ok(adminModerationService.getCommentList(request));
    }

    /** 댓글 검열 AI 판정 카테고리별 통계 */
    @GetMapping("/moderation/comments/stats")
    public ApiResponse<ModerationStatsResponse> commentStats() {
        return ApiResponse.ok(adminModerationService.getCommentStats());
    }

    /** 댓글 검열 단건 상세 */
    @GetMapping("/moderation/comments/{commentId}")
    public ApiResponse<ModerationDetailResponse> commentDetail(@PathVariable Long commentId) {
        return ApiResponse.ok(adminModerationService.getCommentDetail(commentId));
    }

    /** 댓글 HIDDEN → PUBLISHED 복원 */
    @PostMapping("/moderation/comments/{commentId}/restore")
    @RequireAdminPermission({"CONTENT_UPDATE"})
    public ApiResponse<Void> restoreComment(@PathVariable Long commentId) {
        adminModerationService.restoreComment(commentId);
        return ApiResponse.ok(null);
    }

    /** 댓글 → DELETED 확정 삭제 */
    @PostMapping("/moderation/comments/{commentId}/delete")
    @RequireAdminPermission({"CONTENT_DELETE"})
    public ApiResponse<Void> deleteComment(@PathVariable Long commentId) {
        adminModerationService.deleteComment(commentId);
        return ApiResponse.ok(null);
    }

    /** 검열 설정 조회 */
    @GetMapping("/moderation/settings")
    public ApiResponse<ModerationSettingResponse> getSettings() {
        return ApiResponse.ok(toResponse(settingService.getCurrent()));
    }

    /** 검열 설정 변경 */
    @PatchMapping("/moderation/settings")
    @RequireAdminPermission({"AI_UPDATE"})
    public ApiResponse<ModerationSettingResponse> updateSettings(
            @RequestBody ModerationSettingUpdateRequest request
    ) {
        Strictness strictness = settingService.getStrictness();
        double threshold = settingService.getHideThreshold();
        int sanctionThreshold = settingService.getSanctionThreshold();
        int blockDays = settingService.getBlockDays();
        int reportBlurThreshold = settingService.getReportBlurThreshold();
        int postRateWindowSeconds = settingService.getPostRateWindowSeconds();
        int postRateMax = settingService.getPostRateMax();
        int commentRateWindowSeconds = settingService.getCommentRateWindowSeconds();
        int commentRateMax = settingService.getCommentRateMax();
        int inquiryRateWindowSeconds = settingService.getInquiryRateWindowSeconds();
        int inquiryRateMax = settingService.getInquiryRateMax();

        if (request.strictness() != null) {
            try {
                strictness = Strictness.valueOf(request.strictness());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "strictness는 STRICT, NORMAL, LENIENT 중 하나여야 합니다: " + request.strictness());
            }
        }
        if (request.hideThreshold() != null) {
            threshold = request.hideThreshold();
            if (threshold < 0.50 || threshold > 0.95) {
                throw new IllegalArgumentException(
                        "hideThreshold는 0.50 ~ 0.95 범위여야 합니다: " + threshold);
            }
        }
        if (request.sanctionThreshold() != null) {
            sanctionThreshold = request.sanctionThreshold();
            if (sanctionThreshold < 1 || sanctionThreshold > 100) {
                throw new IllegalArgumentException(
                        "sanctionThreshold는 1 ~ 100 범위여야 합니다: " + sanctionThreshold);
            }
        }
        if (request.blockDays() != null) {
            blockDays = request.blockDays();
            if (blockDays < 1 || blockDays > 3650) {
                throw new IllegalArgumentException(
                        "blockDays는 1 ~ 3650 범위여야 합니다: " + blockDays);
            }
        }
        if (request.reportBlurThreshold() != null) {
            reportBlurThreshold = requireRange("reportBlurThreshold", request.reportBlurThreshold(), 1, 1000);
        }
        if (request.postRateWindowSeconds() != null) {
            postRateWindowSeconds = requireRange("postRateWindowSeconds", request.postRateWindowSeconds(), 1, 86400);
        }
        if (request.postRateMax() != null) {
            postRateMax = requireRange("postRateMax", request.postRateMax(), 0, 100000);
        }
        if (request.commentRateWindowSeconds() != null) {
            commentRateWindowSeconds = requireRange("commentRateWindowSeconds", request.commentRateWindowSeconds(), 1, 86400);
        }
        if (request.commentRateMax() != null) {
            commentRateMax = requireRange("commentRateMax", request.commentRateMax(), 0, 100000);
        }
        if (request.inquiryRateWindowSeconds() != null) {
            inquiryRateWindowSeconds = requireRange("inquiryRateWindowSeconds", request.inquiryRateWindowSeconds(), 1, 86400);
        }
        if (request.inquiryRateMax() != null) {
            inquiryRateMax = requireRange("inquiryRateMax", request.inquiryRateMax(), 0, 100000);
        }

        ModerationSetting next = new ModerationSetting();
        next.setStrictness(strictness);
        next.setHideThreshold(threshold);
        next.setSanctionThreshold(sanctionThreshold);
        next.setBlockDays(blockDays);
        next.setReportBlurThreshold(reportBlurThreshold);
        next.setPostRateWindowSeconds(postRateWindowSeconds);
        next.setPostRateMax(postRateMax);
        next.setCommentRateWindowSeconds(commentRateWindowSeconds);
        next.setCommentRateMax(commentRateMax);
        next.setInquiryRateWindowSeconds(inquiryRateWindowSeconds);
        next.setInquiryRateMax(inquiryRateMax);
        settingService.update(next);

        return ApiResponse.ok(toResponse(settingService.getCurrent()));
    }

    private static int requireRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + "는 " + min + " ~ " + max + " 범위여야 합니다: " + value);
        }
        return value;
    }

    private static ModerationSettingResponse toResponse(ModerationSetting s) {
        return new ModerationSettingResponse(
                s.getStrictness().name(),
                s.getHideThreshold(),
                s.getSanctionThreshold(),
                s.getBlockDays(),
                s.getReportBlurThreshold(),
                s.getPostRateWindowSeconds(),
                s.getPostRateMax(),
                s.getCommentRateWindowSeconds(),
                s.getCommentRateMax(),
                s.getInquiryRateWindowSeconds(),
                s.getInquiryRateMax(),
                s.getUpdatedAt());
    }
}
