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
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.PostAiResult;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.community.service.CommunityPostService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/community/posts")
@RequiredArgsConstructor
public class CommunityPostController {

    private final CommunityPostService postService;
    private final PostAiResultMapper aiResultMapper;

    @GetMapping("/hot")
    public ApiResponse<List<HotPostResponse>> getHotPosts(@AuthenticationPrincipal AuthUser authUser) {
        // 로그인 뷰어만 개인 차단 필터 대상 — 비로그인은 필터 없음
        return ApiResponse.ok(postService.getHotPosts(authUser != null ? authUser.id() : null));
    }

    /** 카테고리 탭 뱃지용 전수 집계 — 목록과 동일하게 PUBLISHED 만 센다(enum명 키). */
    @GetMapping("/category-counts")
    public ApiResponse<Map<String, Long>> getCategoryCounts(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        Long viewerId = authUser != null ? authUser.id() : null;
        return ApiResponse.ok(postService.getCategoryCounts(viewerId));
    }

    @GetMapping
    public ApiResponse<PostPageResponse> getPosts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            // 챗봇 추천 모아보기: "12,45,78" — 있으면 다른 필터 무시하고 그 글들만(입력 순서 보존, 최대 20건).
            @RequestParam(required = false) String ids,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        // 로그인 뷰어만 개인 차단 필터 대상 — 비로그인은 필터 없음
        Long viewerId = authUser != null ? authUser.id() : null;
        if (ids != null && !ids.isBlank()) {
            List<Long> idList = java.util.Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> s.matches("\\d+"))
                    .map(Long::valueOf)
                    .toList();
            return ApiResponse.ok(postService.getPostsByIds(idList, viewerId));
        }
        return ApiResponse.ok(postService.getPosts(category, keyword, sort, page, size, viewerId));
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

    @GetMapping("/{postId}/ai-tags")
    public ApiResponse<PostAiResult> getAiTags(@PathVariable Long postId) {
        PostAiResult result = aiResultMapper.findByPostIdAndTaskType(postId, AiTaskType.TAG);
        return ApiResponse.ok(result);
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
