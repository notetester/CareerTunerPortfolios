package com.careertuner.community.moderation.service;

import org.springframework.stereotype.Service;

import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.domain.ModerationView;
import com.careertuner.community.moderation.dto.ModerationDetailResponse;
import com.careertuner.community.moderation.dto.ModerationItemResponse;
import com.careertuner.community.moderation.dto.ModerationListRequest;
import com.careertuner.community.moderation.dto.ModerationPageResponse;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.dto.ModerationStatsResponse;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class AdminModerationService {

    private final PostAiResultMapper aiResultMapper;
    private final CommunityPostMapper postMapper;
    private final PostModerationService moderationService;
    private final ObjectMapper objectMapper;

    public AdminModerationService(PostAiResultMapper aiResultMapper,
                                  CommunityPostMapper postMapper,
                                  PostModerationService moderationService,
                                  ObjectMapper objectMapper) {
        this.aiResultMapper = aiResultMapper;
        this.postMapper = postMapper;
        this.moderationService = moderationService;
        this.objectMapper = objectMapper;
    }

    public ModerationPageResponse getList(ModerationListRequest request) {
        List<ModerationView> views = aiResultMapper.findModerationList(
                request.status(), request.toxic(), request.offset(), request.size());
        int total = aiResultMapper.countModerationList(request.status(), request.toxic());

        List<ModerationItemResponse> items = views.stream()
                .map(this::toItemResponse)
                .toList();

        return new ModerationPageResponse(
                items, total, request.page(), request.size(),
                request.offset() + request.size() < total
        );
    }

    public ModerationDetailResponse getDetail(Long postId) {
        ModerationView view = aiResultMapper.findModerationDetail(postId);
        if (view == null) {
            throw new IllegalArgumentException("검열 결과를 찾을 수 없습니다: postId=" + postId);
        }
        return toDetailResponse(view);
    }

    public void restore(Long postId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다: postId=" + postId);
        }
        postMapper.updateStatus(postId, PostStatus.PUBLISHED.name());
        moderationService.sendRestoredNotification(post);
    }

    public ModerationStatsResponse getStats() {
        List<Map<String, Object>> rows = aiResultMapper.countByAiCategory();
        List<ModerationStatsResponse.CategoryCount> categories = rows.stream()
                .map(row -> new ModerationStatsResponse.CategoryCount(
                        String.valueOf(row.get("category")),
                        ((Number) row.get("cnt")).intValue()
                ))
                .toList();
        int total = categories.stream().mapToInt(ModerationStatsResponse.CategoryCount::count).sum();
        return new ModerationStatsResponse(categories, total);
    }

    public void delete(Long postId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다: postId=" + postId);
        }
        postMapper.updateStatus(postId, PostStatus.DELETED.name());
        moderationService.sendDeletedNotification(post);
    }

    private ModerationItemResponse toItemResponse(ModerationView view) {
        ModerationResult result = parseResult(view.getResultJson());
        return new ModerationItemResponse(
                view.getPostId(),
                view.getTitle(),
                view.isAnonymous() ? "익명" : view.getAuthorName(),
                view.getPostCategory(),
                view.getPostStatus(),
                result != null && result.toxic(),
                result != null ? result.category() : null,
                result != null ? result.confidence() : 0,
                view.getAttemptCount(),
                view.getCreatedAt(),
                view.getCompletedAt()
        );
    }

    private ModerationDetailResponse toDetailResponse(ModerationView view) {
        ModerationResult result = parseResult(view.getResultJson());
        return new ModerationDetailResponse(
                view.getPostId(),
                view.getTitle(),
                view.getContent(),
                view.isAnonymous() ? "익명" : view.getAuthorName(),
                view.getPostCategory(),
                view.getPostStatus(),
                result != null && result.toxic(),
                result != null ? result.category() : null,
                result != null ? result.confidence() : 0,
                view.getModel(),
                view.getAttemptCount(),
                view.getCreatedAt(),
                view.getCompletedAt()
        );
    }

    private ModerationResult parseResult(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ModerationResult.class);
        } catch (Exception e) {
            return null;
        }
    }
}
