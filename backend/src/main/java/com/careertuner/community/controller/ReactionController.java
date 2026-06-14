package com.careertuner.community.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.dto.ToggleReactionRequest;
import com.careertuner.community.dto.ToggleReactionResponse;
import com.careertuner.community.service.ReactionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/community/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping
    public ApiResponse<ToggleReactionResponse> toggleReaction(
            @Validated @RequestBody ToggleReactionRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        boolean active = reactionService.toggleReaction(request, authUser.id());
        return ApiResponse.ok(new ToggleReactionResponse(active));
    }
}
