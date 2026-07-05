package com.careertuner.community.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.service.ReactionSettingService;

import lombok.RequiredArgsConstructor;

/**
 * 리액션 유지/해지 설정 — 게시글 수정 시 내 리액션(like/dislike/recommend/disrecommend/bookmark)을
 * 유지(keep, 기본)할지 해지(release)할지. 저장은 user_privacy_policy.policy_json 의
 * 별도 최상위 키(reactionRetention)로, privacy 코어를 거치지 않는 커뮤니티 소유 API.
 */
@RestController
@RequestMapping("/api/community/reaction-settings")
@RequiredArgsConstructor
public class ReactionSettingController {

    private final ReactionSettingService settingService;

    @GetMapping
    public ApiResponse<Map<String, String>> getSettings(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(settingService.getRetentionSettings(authUser.id()));
    }

    @PutMapping
    public ApiResponse<Map<String, String>> updateSettings(
            @RequestBody Map<String, String> settings,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(settingService.updateRetentionSettings(authUser.id(), settings));
    }
}
