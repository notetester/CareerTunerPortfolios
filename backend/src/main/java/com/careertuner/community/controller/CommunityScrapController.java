package com.careertuner.community.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.dto.ScrapResponse;
import com.careertuner.community.service.PostScrapService;

import lombok.RequiredArgsConstructor;

/**
 * 스크랩 — 스냅샷 보존형(원본 수정/삭제와 무관하게 열람).
 * 즐겨찾기(BOOKMARK 리액션, 링크형)와 별개 API.
 */
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityScrapController {

    private final PostScrapService scrapService;

    /** 스크랩 토글 요청 — anonymous 만 옵션. */
    public record ToggleScrapRequest(Boolean anonymous) {}

    @PostMapping("/posts/{postId}/scrap")
    public ApiResponse<Map<String, Object>> toggleScrap(
            @PathVariable Long postId,
            @RequestBody(required = false) ToggleScrapRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        boolean anonymous = request != null && Boolean.TRUE.equals(request.anonymous());
        return ApiResponse.ok(scrapService.toggleScrap(postId, anonymous, authUser.id()));
    }

    @GetMapping("/scraps")
    public ApiResponse<ScrapResponse.Page> getMyScraps(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(scrapService.getMyScraps(authUser.id(), page, size));
    }

    /** 스크랩 상세 열람 — 스냅샷 기반. 원본이 삭제됐어도 열람 가능("원본이 삭제된 글" 배지). */
    @GetMapping("/scraps/{scrapId}")
    public ApiResponse<ScrapResponse> getScrapDetail(
            @PathVariable Long scrapId,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(scrapService.getScrapDetail(scrapId, authUser.id()));
    }

    @DeleteMapping("/scraps/{scrapId}")
    public ApiResponse<Void> deleteScrap(
            @PathVariable Long scrapId,
            @AuthenticationPrincipal AuthUser authUser) {
        scrapService.deleteScrap(scrapId, authUser.id());
        return ApiResponse.ok();
    }
}
