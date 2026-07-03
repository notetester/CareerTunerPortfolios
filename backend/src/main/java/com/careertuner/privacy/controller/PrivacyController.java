package com.careertuner.privacy.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.privacy.dto.ConversationBlockRequest;
import com.careertuner.privacy.dto.ConversationBlockResponse;
import com.careertuner.privacy.dto.IpBlockResponse;
import com.careertuner.privacy.dto.PrivacyPolicyResponse;
import com.careertuner.privacy.dto.PrivacyPolicyUpdateRequest;
import com.careertuner.privacy.dto.UserBlockByContentRequest;
import com.careertuner.privacy.dto.UserBlockRequest;
import com.careertuner.privacy.dto.UserBlockResponse;
import com.careertuner.privacy.dto.UserBlockUpdateRequest;
import com.careertuner.privacy.service.PrivacyPolicyService;

import lombok.RequiredArgsConstructor;

/** 개인 차단/허용 정책 (docs/PERSONAL_BLOCK_POLICY.md §6). */
@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
@Validated
public class PrivacyController {

    private final PrivacyPolicyService privacyPolicyService;

    @GetMapping("/policy")
    public ApiResponse<PrivacyPolicyResponse> policy(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(privacyPolicyService.getPolicy(authUser.id()));
    }

    @PutMapping("/policy")
    public ApiResponse<PrivacyPolicyResponse> updatePolicy(
            @Validated @RequestBody PrivacyPolicyUpdateRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(privacyPolicyService.updatePolicy(authUser.id(), request));
    }

    @GetMapping("/blocks/users")
    public ApiResponse<List<UserBlockResponse>> userBlocks(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(privacyPolicyService.listUserBlocks(authUser.id()));
    }

    @PostMapping("/blocks/users")
    public ApiResponse<UserBlockResponse> blockUser(
            @Validated @RequestBody UserBlockRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(privacyPolicyService.blockUser(authUser.id(), request));
    }

    /** 콘텐츠 id 기반 차단 — 익명 글/댓글 작성자용(클라이언트는 작성자 id 를 모름, 익명성 유지). */
    @PostMapping("/blocks/users/by-content")
    public ApiResponse<UserBlockResponse> blockUserByContent(
            @Validated @RequestBody UserBlockByContentRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(privacyPolicyService.blockUserByContent(authUser.id(), request));
    }

    @PutMapping("/blocks/users/{blockId}")
    public ApiResponse<UserBlockResponse> updateUserBlock(
            @PathVariable Long blockId,
            @Validated @RequestBody UserBlockUpdateRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(privacyPolicyService.updateUserBlock(authUser.id(), blockId, request));
    }

    @DeleteMapping("/blocks/users/{blockId}")
    public ApiResponse<Void> unblockUser(@PathVariable Long blockId,
                                         @AuthenticationPrincipal AuthUser authUser) {
        privacyPolicyService.unblockUser(authUser.id(), blockId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/blocks/ips")
    public ApiResponse<List<IpBlockResponse>> ipBlocks(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(privacyPolicyService.listIpBlocks(authUser.id()));
    }

    @DeleteMapping("/blocks/ips/{ipBlockId}")
    public ApiResponse<Void> deleteIpBlock(@PathVariable Long ipBlockId,
                                           @AuthenticationPrincipal AuthUser authUser) {
        privacyPolicyService.deleteIpBlock(authUser.id(), ipBlockId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/blocks/conversations")
    public ApiResponse<List<ConversationBlockResponse>> conversationBlocks(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(privacyPolicyService.listConversationBlocks(authUser.id()));
    }

    @PostMapping("/blocks/conversations")
    public ApiResponse<ConversationBlockResponse> blockConversation(
            @Validated @RequestBody ConversationBlockRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(privacyPolicyService.blockConversation(authUser.id(), request));
    }

    @PutMapping("/blocks/conversations/{blockId}")
    public ApiResponse<ConversationBlockResponse> updateConversationBlock(
            @PathVariable Long blockId,
            @RequestBody Map<String, String> flags,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(privacyPolicyService.updateConversationBlock(authUser.id(), blockId, flags));
    }

    @DeleteMapping("/blocks/conversations/{blockId}")
    public ApiResponse<Void> unblockConversation(@PathVariable Long blockId,
                                                 @AuthenticationPrincipal AuthUser authUser) {
        privacyPolicyService.unblockConversation(authUser.id(), blockId);
        return ApiResponse.ok(null);
    }
}
