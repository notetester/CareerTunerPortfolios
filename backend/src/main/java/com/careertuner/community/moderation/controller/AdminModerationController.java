package com.careertuner.community.moderation.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.dto.ModerationTestRequest;
import com.careertuner.community.moderation.dto.ModerationTestResponse;
import com.careertuner.community.moderation.service.PostModerationService;

/**
 * AI 검열 디버깅용 관리자 엔드포인트.
 * DB 기록 없이 judge()만 호출하여 판정 결과를 즉시 반환한다.
 */
@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AdminModerationController {

    private final PostModerationService moderationService;

    public AdminModerationController(PostModerationService moderationService) {
        this.moderationService = moderationService;
    }

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
}
