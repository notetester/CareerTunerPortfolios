package com.careertuner.admin.chatbot.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.chatbot.dto.ChatbotReferencePage;
import com.careertuner.admin.chatbot.service.AdminChatbotReferenceService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 AI 상담 운영 콘솔 참조 대화 표(F3-A, 답한 대화 로그). /api/admin/** 규칙으로 자동 관리자 전용.
 * 디자인 av-table(시각/질문/FAQ/유사도/결과) 소스. 읽기 전용.
 */
@RestController
@RequestMapping("/api/admin/chatbot")
@RequireAdminPermission({"AI_READ"})
@RequiredArgsConstructor
public class AdminChatbotReferenceController {

    private final AdminChatbotReferenceService referenceService;

    /** 답한 대화 로그. 기간 from/to(ISO yyyy-MM-dd, 일 inclusive, 미지정 시 최근 7일) + page/size. */
    @GetMapping("/references")
    public ApiResponse<ChatbotReferencePage> references(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(referenceService.getReferences(authUser, from, to, page, size));
    }
}
