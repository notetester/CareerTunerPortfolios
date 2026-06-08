package com.careertuner.admin.fitanalysis.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisDetailResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListItemResponse;
import com.careertuner.admin.fitanalysis.service.AdminFitAnalysisService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/fit-analyses")
@RequiredArgsConstructor
public class AdminFitAnalysisController {

    private final AdminFitAnalysisService adminFitAnalysisService;

    @GetMapping
    public ApiResponse<List<AdminFitAnalysisListItemResponse>> list(@AuthenticationPrincipal AuthUser authUser) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminFitAnalysisDetailResponse> get(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long id) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminFitAnalysisService.get(id));
    }

    private static void requireAdmin(AuthUser authUser) {
        if (!"ADMIN".equals(authUser.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
    }
}
