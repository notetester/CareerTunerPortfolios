package com.careertuner.community.controller;

import java.util.List;

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
import com.careertuner.community.dto.CommentResponse;
import com.careertuner.community.dto.CreateCommentRequest;
import com.careertuner.community.dto.UpdateCommentRequest;
import com.careertuner.community.service.CommunityCommentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityCommentController {

    private final CommunityCommentService commentService;

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<List<CommentResponse>> getComments(
            @PathVariable Long postId,
            @AuthenticationPrincipal AuthUser authUser) {
        Long userId = authUser != null ? authUser.id() : null;
        return ApiResponse.ok(commentService.getComments(postId, userId));
    }

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<CommentResponse> createComment(
            @PathVariable Long postId,
            @Validated @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(commentService.createComment(postId, request, authUser.id()));
    }

    @PutMapping("/comments/{commentId}")
    public ApiResponse<CommentResponse> updateComment(
            @PathVariable Long commentId,
            @Validated @RequestBody UpdateCommentRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(commentService.updateComment(commentId, request.content(), authUser.id()));
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal AuthUser authUser) {
        commentService.deleteComment(commentId, authUser.id());
        return ApiResponse.ok();
    }
}
