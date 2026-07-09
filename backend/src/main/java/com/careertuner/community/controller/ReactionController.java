package com.careertuner.community.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.dto.PostReactorResponse;
import com.careertuner.community.dto.ToggleReactionRequest;
import com.careertuner.community.dto.ToggleReactionResponse;
import com.careertuner.community.service.ReactionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    /** 리액션 토글 — 같은 축 반대 클릭 시 교체, 같은 것 재클릭 시 취소. 응답은 토글 후 카운트. */
    @PostMapping("/reactions")
    public ApiResponse<ToggleReactionResponse> toggleReaction(
            @Validated @RequestBody ToggleReactionRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(reactionService.toggleReaction(request, authUser.id()));
    }

    /** 게시글 반응자 목록 — 익명 리액션은 본인 것만 포함(타인 시점 제외 규칙). */
    @GetMapping("/posts/{postId}/reactions")
    public ApiResponse<List<PostReactorResponse>> getPostReactors(
            @PathVariable Long postId,
            @AuthenticationPrincipal AuthUser authUser) {
        Long viewerId = authUser != null ? authUser.id() : null;
        return ApiResponse.ok(reactionService.getPostReactors(postId, viewerId));
    }
}
