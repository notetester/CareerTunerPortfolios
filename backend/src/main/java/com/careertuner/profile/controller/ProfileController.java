package com.careertuner.profile.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.profile.dto.ProfileAiResponse;
import com.careertuner.profile.dto.ProfileCompletenessResponse;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.dto.UserProfileResponse;
import com.careertuner.profile.service.ProfileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService service;

    @GetMapping
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.me(authUser));
    }

    @PutMapping
    public ApiResponse<UserProfileResponse> save(@AuthenticationPrincipal AuthUser authUser,
                                                 @RequestBody UserProfileRequest request) {
        return ApiResponse.ok(service.save(authUser, request));
    }

    @PostMapping("/ai/summary")
    public ApiResponse<ProfileAiResponse> summarize(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.summarize(authUser));
    }

    @PostMapping("/ai/skills")
    public ApiResponse<ProfileAiResponse> extractSkills(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.extractSkills(authUser));
    }

    @PostMapping("/ai/completeness")
    public ApiResponse<ProfileCompletenessResponse> diagnoseCompleteness(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.diagnoseCompleteness(authUser));
    }
}
