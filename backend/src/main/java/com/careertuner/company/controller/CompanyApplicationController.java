package com.careertuner.company.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.company.dto.CompanyApplicationRequest;
import com.careertuner.company.dto.CompanyApplicationResponse;
import com.careertuner.company.dto.CompanyProfileResponse;
import com.careertuner.company.service.CompanyApplicationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 기업 계정 신청/조회 — 사용자 측 API. 관리자 처리(승인/반려)는 /api/admin/company/** 에 있다. */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyApplicationController {

    private final CompanyApplicationService companyApplicationService;

    @PostMapping("/applications")
    public ApiResponse<CompanyApplicationResponse> apply(@AuthenticationPrincipal AuthUser authUser,
                                                         @Valid @RequestBody CompanyApplicationRequest request) {
        return ApiResponse.ok(companyApplicationService.apply(authUser.id(), request));
    }

    /** 내 최신 신청 1건. 신청 이력이 없으면 data=null. */
    @GetMapping("/applications/me")
    public ApiResponse<CompanyApplicationResponse> myApplication(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(companyApplicationService.myLatestApplication(authUser.id()));
    }

    /** 내 기업 프로필. 승인 전이면 data=null. */
    @GetMapping("/profile/me")
    public ApiResponse<CompanyProfileResponse> myProfile(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(companyApplicationService.myProfile(authUser.id()));
    }
}
