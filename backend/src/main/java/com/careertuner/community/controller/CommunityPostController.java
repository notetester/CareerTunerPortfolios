package com.careertuner.community.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.dto.CreatePostRequest;
import com.careertuner.community.dto.HotPostResponse;
import com.careertuner.community.dto.PostDetailResponse;
import com.careertuner.community.dto.PostPageResponse;
import com.careertuner.community.dto.UpdatePostRequest;
import com.careertuner.community.service.CommunityPostService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/community/posts")
@RequiredArgsConstructor
public class CommunityPostController {

    private final CommunityPostService postService;

    @GetMapping("/hot")
    public ApiResponse<List<HotPostResponse>> getHotPosts() {
        return ApiResponse.ok(postService.getHotPosts());
    }

    @GetMapping
    public ApiResponse<PostPageResponse> getPosts(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(postService.getPosts(category, sort, page, size));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDetailResponse> getPostDetail(
            @PathVariable Long postId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        Long userId = authUser != null ? authUser.id() : null;
        return ApiResponse.ok(postService.getPostDetail(postId, userId));
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> createPost(
            @Validated @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        Long postId = postService.createPost(request, authUser.id());
        return ApiResponse.ok(Map.of("postId", postId));
    }

    @PutMapping("/{postId}")
    public ApiResponse<Void> updatePost(
            @PathVariable Long postId, 
            @Validated @RequestBody UpdatePostRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        postService.updatePost(postId, request, authUser.id());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        postService.deletePost(postId, authUser.id());
        return ApiResponse.ok();
    }
}
