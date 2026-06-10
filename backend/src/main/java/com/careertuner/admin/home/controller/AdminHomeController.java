package com.careertuner.admin.home.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.home.dto.AdminHomeSummaryResponse;
import com.careertuner.admin.home.service.AdminHomeService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/home")
@RequiredArgsConstructor
public class AdminHomeController {

    private final AdminHomeService adminHomeService;

    @GetMapping("/summary")
    public ApiResponse<AdminHomeSummaryResponse> summary(@AuthenticationPrincipal AuthUser authUser) {
        if (!"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
        return ApiResponse.ok(adminHomeService.getSummary());
    }
}
