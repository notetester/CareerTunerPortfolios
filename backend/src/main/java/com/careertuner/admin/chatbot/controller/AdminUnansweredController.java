package com.careertuner.admin.chatbot.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.chatbot.dto.AdminUnansweredQuestionResponse;
import com.careertuner.admin.chatbot.dto.AdminUnansweredStatusRequest;
import com.careertuner.admin.chatbot.service.AdminUnansweredService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 AI 상담 운영 패널 1단계: 챗봇이 FAQ로 답 못한 질문 조회·처리.
 * /api/admin/** 경로 규칙(SecurityConfig)으로 자동 관리자 전용.
 */
@RestController
@RequestMapping("/api/admin/chatbot")
@RequiredArgsConstructor
public class AdminUnansweredController {

    private final AdminUnansweredService unansweredService;

    /** 답 못한 질문 그룹 목록(기본 status=NEW, 빈도 desc/최신 desc). */
    @GetMapping("/unanswered")
    public ApiResponse<List<AdminUnansweredQuestionResponse>> getUnanswered(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "NEW") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(unansweredService.getUnanswered(authUser, status, page, size));
    }

    /** 그룹 상태변경(REVIEWED/DISMISSED). 대표 id 가 속한 토픽 전체를 옮긴다. */
    @PatchMapping("/unanswered/{id}/status")
    public ApiResponse<Void> updateStatus(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestBody AdminUnansweredStatusRequest request) {
        unansweredService.updateStatus(authUser, id, request.status());
        return ApiResponse.ok();
    }
}
