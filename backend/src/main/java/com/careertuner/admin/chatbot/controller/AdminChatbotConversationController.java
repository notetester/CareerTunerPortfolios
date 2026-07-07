package com.careertuner.admin.chatbot.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.chatbot.dto.AdminChatbotConversationRow;
import com.careertuner.admin.chatbot.mapper.AdminChatbotConversationMapper;
import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 챗봇 대화 세션 콘솔 API — 전역 세션 목록 + 삭제(정리). 메시지 본문은 목록에 싣지 않는다.
 * (TripTogether assistant 세션 조회/삭제 축 이식)
 */
@RestController
@RequestMapping("/api/admin/chatbot/conversations")
@RequiredArgsConstructor
public class AdminChatbotConversationController {

    private final AdminChatbotConversationMapper mapper;
    private final AdminActionLogService actionLogService;

    @GetMapping
    public ApiResponse<List<AdminChatbotConversationRow>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "200") int limit) {
        AdminAccess.requireAdmin(authUser);
        int capped = Math.max(1, Math.min(limit, 1000));
        return ApiResponse.ok(mapper.findRecent(userId, capped));
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long conversationId) {
        AdminAccess.requireAdmin(authUser);
        int deleted = mapper.deleteConversation(conversationId);
        actionLogService.record(authUser, null, "CHATBOT_CONVERSATION_DELETED", "CHATBOT_CONVERSATION",
                null, "conversationId=" + conversationId + ", deleted=" + deleted, null);
        return ApiResponse.ok(null);
    }
}
