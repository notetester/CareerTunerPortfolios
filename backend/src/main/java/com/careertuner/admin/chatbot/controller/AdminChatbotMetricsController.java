package com.careertuner.admin.chatbot.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.chatbot.dto.ChatbotMetricsResponse;
import com.careertuner.admin.chatbot.service.AdminChatbotMetricsService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 AI 상담 운영 콘솔 메트릭 밴드(3단계-2). /api/admin/** 규칙으로 자동 관리자 전용.
 * S1: FAQ 공백 카드만 채운다(나머지 3카드는 후속 단계).
 */
@RestController
@RequestMapping("/api/admin/chatbot")
@RequiredArgsConstructor
public class AdminChatbotMetricsController {

    private final AdminChatbotMetricsService metricsService;

    /** 메트릭 밴드. 기간 from/to(ISO yyyy-MM-dd, 일 inclusive). 미지정 시 최근 7일. */
    @GetMapping("/metrics")
    public ApiResponse<ChatbotMetricsResponse> metrics(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(metricsService.getMetrics(authUser, from, to));
    }
}
