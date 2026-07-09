package com.careertuner.community.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.service.CommunitySubscriptionService;

import lombok.RequiredArgsConstructor;

/** 글/댓글 구독 토글 — 새 댓글/답글 알림(POST_WATCH_COMMENT / COMMENT_WATCH_REPLY). */
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunitySubscriptionController {

    private final CommunitySubscriptionService subscriptionService;

    @PostMapping("/posts/{postId}/subscription")
    public ApiResponse<Map<String, Boolean>> togglePostSubscription(
            @PathVariable Long postId,
            @AuthenticationPrincipal AuthUser authUser) {
        boolean active = subscriptionService.togglePostSubscription(postId, authUser.id());
        return ApiResponse.ok(Map.of("active", active));
    }

    @PostMapping("/comments/{commentId}/subscription")
    public ApiResponse<Map<String, Boolean>> toggleCommentSubscription(
            @PathVariable Long commentId,
            @AuthenticationPrincipal AuthUser authUser) {
        boolean active = subscriptionService.toggleCommentSubscription(commentId, authUser.id());
        return ApiResponse.ok(Map.of("active", active));
    }
}
