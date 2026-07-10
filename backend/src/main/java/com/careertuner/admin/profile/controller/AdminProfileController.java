package com.careertuner.admin.profile.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.profile.dto.UserProfileResponse;
import com.careertuner.profile.service.ProfileService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/profiles")
@RequireAdminPermission({"PROFILE_READ", "MEMBER_ADMIN"})
@RequiredArgsConstructor
public class AdminProfileController {

    private final ProfileService service;

    @GetMapping
    public ApiResponse<List<UserProfileResponse>> profiles(@AuthenticationPrincipal AuthUser authUser,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.adminProfiles(authUser, keyword, limit));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserProfileResponse> profile(@AuthenticationPrincipal AuthUser authUser,
                                                    @PathVariable Long userId) {
        return ApiResponse.ok(service.adminProfile(authUser, userId));
    }
}
