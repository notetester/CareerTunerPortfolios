package com.careertuner.community.search;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.web.ApiResponse;

/**
 * 관리자: 커뮤니티 글 일괄 임베딩. FAQ 의 /api/admin/faq/embed-all 패턴 동일.
 * 스키마(community_post_embedding) 적용 후 1회 실행해 기존 글을 적재한다.
 */
@RestController
@RequestMapping("/api/admin/community")
@RequireAdminPermission({"AI_OPERATION_MANAGE", "AI_ADMIN"})
public class CommunityEmbeddingAdminController {

    private final CommunityEmbeddingService embeddingService;

    public CommunityEmbeddingAdminController(CommunityEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /** POST /api/admin/community/embed-all — 미임베딩 PUBLISHED 글 일괄 임베딩 */
    @PostMapping("/embed-all")
    public ApiResponse<Map<String, Object>> embedAll() {
        int count = embeddingService.embedAllPosts();
        return ApiResponse.ok(Map.of("embeddedCount", count));
    }
}
