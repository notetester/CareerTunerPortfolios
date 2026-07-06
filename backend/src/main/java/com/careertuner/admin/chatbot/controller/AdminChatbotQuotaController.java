package com.careertuner.admin.chatbot.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.support.chatbot.quota.ChatbotQuotaPolicy;
import com.careertuner.support.chatbot.quota.ChatbotQuotaPolicyRequest;
import com.careertuner.support.chatbot.quota.ChatbotQuotaPolicyService;

import lombok.RequiredArgsConstructor;

/**
 * AI 챗봇 일일 사용 쿼터 정책 관리자 콘솔 API.
 * OFF(무제약) ↔ ON(로그인 사용자 하루 N회) 토글 + 한도 편집. 집행은 챗봇 요청 경로(ChatbotController.ask).
 */
@RestController
@RequestMapping("/api/admin/chatbot/quota-policy")
@RequiredArgsConstructor
public class AdminChatbotQuotaController {

    private final ChatbotQuotaPolicyService service;
    private final AdminActionLogService actionLogService;

    @GetMapping
    public ApiResponse<ChatbotQuotaPolicy> get(@AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(service.getCurrent());
    }

    @PatchMapping
    public ApiResponse<ChatbotQuotaPolicy> update(@AuthenticationPrincipal AuthUser authUser,
                                                  @RequestBody ChatbotQuotaPolicyRequest request) {
        AdminAccess.requireAdmin(authUser);
        ChatbotQuotaPolicy before = service.getCurrent();
        boolean enabled = request.enabled() != null ? request.enabled() : before.enabled();
        int dailyLimit = request.dailyLimit() != null
                ? requireRange("dailyLimit", request.dailyLimit(), 1, 1000000)
                : before.dailyLimit();

        ChatbotQuotaPolicy after = service.update(enabled, dailyLimit, authUser.id());
        actionLogService.record(authUser, null, "CHATBOT_QUOTA_POLICY_UPDATED", "CHATBOT_QUOTA_POLICY",
                snapshot(before), snapshot(after), request.reason());
        return ApiResponse.ok(after);
    }

    private static int requireRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + "는 " + min + " ~ " + max + " 범위여야 합니다: " + value);
        }
        return value;
    }

    private static String snapshot(ChatbotQuotaPolicy p) {
        return "{\"enabled\":" + p.enabled() + ",\"dailyLimit\":" + p.dailyLimit() + "}";
    }
}
