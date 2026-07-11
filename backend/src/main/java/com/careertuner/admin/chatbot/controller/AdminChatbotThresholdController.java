package com.careertuner.admin.chatbot.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.chatbot.dto.ThresholdPreviewResponse;
import com.careertuner.admin.chatbot.service.AdminChatbotThresholdService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 AI 상담 운영 콘솔 — 유사도 임계값 슬라이더 미리보기(F2, 디자인 rg-cfg 좌측 슬라이더).
 * /api/admin/** 규칙으로 자동 관리자 전용. <b>읽기 전용 — 실 챗봇 컷오프를 바꾸지 않는다(저장/적용 없음, §1-Q3).</b>
 */
@RestController
@RequestMapping("/api/admin/chatbot/threshold")
@RequireAdminPermission({"AI_READ"})
@RequiredArgsConstructor
public class AdminChatbotThresholdController {

    private final AdminChatbotThresholdService thresholdService;

    /**
     * 후보 임계값 미리보기. threshold 미만인 턴 수(공백)를 읽기 전용 계산 + 0.05 폭 히스토그램.
     * 디자인 슬라이더 범위는 0.50~0.95 이나 범위 밖 값도 [0.0,1.0] 으로 보정해 안전하게 응답한다.
     */
    @GetMapping("/preview")
    public ApiResponse<ThresholdPreviewResponse> preview(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) Double threshold) {
        return ApiResponse.ok(thresholdService.preview(authUser, threshold));
    }
}
