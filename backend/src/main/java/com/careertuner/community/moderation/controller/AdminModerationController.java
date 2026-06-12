package com.careertuner.community.moderation.controller;

import org.springframework.security.access.prepost.PreAuthorize;
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
import com.careertuner.community.moderation.domain.ModerationSetting;
import com.careertuner.community.moderation.domain.Strictness;
import com.careertuner.community.moderation.dto.ModerationDetailResponse;
import com.careertuner.community.moderation.dto.ModerationListRequest;
import com.careertuner.community.moderation.dto.ModerationPageResponse;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.dto.ModerationSettingResponse;
import com.careertuner.community.moderation.dto.ModerationSettingUpdateRequest;
import com.careertuner.community.moderation.dto.ModerationStatsResponse;
import com.careertuner.community.moderation.dto.ModerationTestRequest;
import com.careertuner.community.moderation.dto.ModerationTestResponse;
import com.careertuner.community.moderation.service.AdminModerationService;
import com.careertuner.community.moderation.service.ModerationSettingService;
import com.careertuner.community.moderation.service.PostModerationService;

/**
 * AI 검열 관리자 엔드포인트.
 */
@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AdminModerationController {

    private final PostModerationService moderationService;
    private final AdminModerationService adminModerationService;
    private final ModerationSettingService settingService;

    public AdminModerationController(PostModerationService moderationService,
                                     AdminModerationService adminModerationService,
                                     ModerationSettingService settingService) {
        this.moderationService = moderationService;
        this.adminModerationService = adminModerationService;
        this.settingService = settingService;
    }

    /** 검열 테스트 (DB 기록 없이 judge()만 호출) */
    @PostMapping("/moderation-test")
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

    /** HIDDEN → PUBLISHED 복원 */
    @PostMapping("/moderation/{postId}/restore")
    public ApiResponse<Void> restore(@PathVariable Long postId) {
        adminModerationService.restore(postId);
        return ApiResponse.ok(null);
    }

    /** HIDDEN → DELETED 확정 삭제 */
    @PostMapping("/moderation/{postId}/delete")
    public ApiResponse<Void> delete(@PathVariable Long postId) {
        adminModerationService.delete(postId);
        return ApiResponse.ok(null);
    }

    /** AI 판정 카테고리별 통계 */
    @GetMapping("/moderation/stats")
    public ApiResponse<ModerationStatsResponse> stats() {
        return ApiResponse.ok(adminModerationService.getStats());
    }

    /** 검열 설정 조회 */
    @GetMapping("/moderation/settings")
    public ApiResponse<ModerationSettingResponse> getSettings() {
        ModerationSetting setting = settingService.getCurrent();
        return ApiResponse.ok(new ModerationSettingResponse(
                setting.getStrictness().name(),
                setting.getHideThreshold(),
                setting.getUpdatedAt()
        ));
    }

    /** 검열 설정 변경 */
    @PatchMapping("/moderation/settings")
    public ApiResponse<ModerationSettingResponse> updateSettings(
            @RequestBody ModerationSettingUpdateRequest request
    ) {
        Strictness strictness = settingService.getStrictness();
        double threshold = settingService.getHideThreshold();

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

        settingService.update(strictness, threshold);

        ModerationSetting updated = settingService.getCurrent();
        return ApiResponse.ok(new ModerationSettingResponse(
                updated.getStrictness().name(),
                updated.getHideThreshold(),
                updated.getUpdatedAt()
        ));
    }
}
