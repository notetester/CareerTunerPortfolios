package com.careertuner.admin.company.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.company.dto.AdminRejectRequest;
import com.careertuner.admin.common.AdminAccess;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.company.dto.CompanyApplicationResponse;
import com.careertuner.company.service.CompanyApplicationService;

import lombok.RequiredArgsConstructor;

/** 기업 신청 승인/반려 콘솔 API. */
@RestController
@RequestMapping("/api/admin/company/applications")
@RequiredArgsConstructor
public class AdminCompanyApplicationController {

    private final CompanyApplicationService companyApplicationService;

    @GetMapping
    public ApiResponse<List<CompanyApplicationResponse>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String status) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(companyApplicationService.adminList(status));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<CompanyApplicationResponse> approve(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long id) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(companyApplicationService.approve(authUser.id(), id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<CompanyApplicationResponse> reject(@AuthenticationPrincipal AuthUser authUser,
                                                          @PathVariable Long id,
                                                          @RequestBody AdminRejectRequest request) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(companyApplicationService.reject(authUser.id(), id, request.reason()));
    }
}
