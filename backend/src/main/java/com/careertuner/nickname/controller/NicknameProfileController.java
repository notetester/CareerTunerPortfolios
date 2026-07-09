package com.careertuner.nickname.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.nickname.dto.ConversationProfileRequest;
import com.careertuner.nickname.dto.ConversationProfileResponse;
import com.careertuner.nickname.dto.DisplayNameResponse;
import com.careertuner.nickname.dto.NicknameProfileRequest;
import com.careertuner.nickname.dto.NicknameProfileResponse;
import com.careertuner.nickname.service.NicknameProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 복수 닉네임 프로필 + 채팅방 전용 프로필 + 표시명 해석 API.
 *
 * <p>표시명 해석(GET /api/nicknames/resolve)은 다른 도메인(community/collaboration)이
 * 작성자 표시를 조회하기 위한 통합 지점이다(그 도메인 컨트롤러는 수정하지 않는다).</p>
 */
@RestController
@RequestMapping("/api/nicknames")
@RequiredArgsConstructor
public class NicknameProfileController {

    private final NicknameProfileService service;

    // ── 내 닉네임 프로필 관리 ──

    @GetMapping
    public ApiResponse<List<NicknameProfileResponse>> myProfiles(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.myProfiles(authUser.id()));
    }

    @PostMapping
    public ApiResponse<NicknameProfileResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                       @Valid @RequestBody NicknameProfileRequest request) {
        return ApiResponse.ok(service.create(authUser.id(), request));
    }

    @PutMapping("/{profileId}")
    public ApiResponse<NicknameProfileResponse> update(@AuthenticationPrincipal AuthUser authUser,
                                                       @PathVariable Long profileId,
                                                       @Valid @RequestBody NicknameProfileRequest request) {
        return ApiResponse.ok(service.update(authUser.id(), profileId, request));
    }

    @DeleteMapping("/{profileId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long profileId) {
        service.delete(authUser.id(), profileId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{profileId}/default")
    public ApiResponse<NicknameProfileResponse> setDefault(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long profileId) {
        return ApiResponse.ok(service.setDefault(authUser.id(), profileId));
    }

    /** 닉네임 전역 사용 가능 여부. */
    @GetMapping("/availability")
    public ApiResponse<Boolean> availability(@RequestParam String nickname,
                                             @RequestParam(required = false) Long excludeProfileId) {
        return ApiResponse.ok(service.isNicknameAvailable(nickname, excludeProfileId));
    }

    // ── 채팅방 전용 프로필 ──

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<ConversationProfileResponse> getConversationProfile(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long conversationId) {
        return ApiResponse.ok(service.getConversationProfile(authUser.id(), conversationId));
    }

    @PutMapping("/conversations/{conversationId}")
    public ApiResponse<ConversationProfileResponse> setConversationProfile(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long conversationId,
            @RequestBody ConversationProfileRequest request) {
        return ApiResponse.ok(service.setConversationProfile(authUser.id(), conversationId, request));
    }

    // ── 표시명 해석(다른 도메인 통합 지점) ──

    @GetMapping("/resolve")
    public ApiResponse<DisplayNameResponse> resolve(@RequestParam Long accountId,
                                                    @RequestParam(required = false) Long profileId) {
        return ApiResponse.ok(service.resolveDisplayName(accountId, profileId));
    }
}
