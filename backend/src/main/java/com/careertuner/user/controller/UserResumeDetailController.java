package com.careertuner.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.user.dto.UserResumeDetailRequest;
import com.careertuner.user.dto.UserResumeDetailResponse;
import com.careertuner.user.service.UserAccountService;

import lombok.RequiredArgsConstructor;

/**
 * 이력서 상세 스펙 API(사람인/잡코리아식).
 *
 * <p>기존 /api/profile(다른 도메인 소유)과 충돌하지 않도록 별도 경로에 둔다.
 * user_profile 의 desired_job/skills 와 별개로 확장 스펙(수상/대외활동/희망 근무조건 등)을 관리한다.</p>
 */
@RestController
@RequestMapping("/api/resume-detail")
@RequiredArgsConstructor
public class UserResumeDetailController {

    private final UserAccountService service;

    @GetMapping
    public ApiResponse<UserResumeDetailResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.getResumeDetail(authUser.id()));
    }

    @PutMapping
    public ApiResponse<UserResumeDetailResponse> save(@AuthenticationPrincipal AuthUser authUser,
                                                      @RequestBody UserResumeDetailRequest request) {
        return ApiResponse.ok(service.saveResumeDetail(authUser.id(), request));
    }
}
