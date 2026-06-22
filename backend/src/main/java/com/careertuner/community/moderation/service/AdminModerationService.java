package com.careertuner.community.moderation.service;

import org.springframework.stereotype.Service;

import com.careertuner.community.domain.CommentStatus;
import com.careertuner.community.domain.CommunityComment;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostCategory;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.domain.ModerationView;
import com.careertuner.community.moderation.dto.ModerationDetailResponse;
import com.careertuner.community.moderation.dto.ModerationItemResponse;
import com.careertuner.community.moderation.dto.ModerationListRequest;
import com.careertuner.community.moderation.dto.ModerationPageResponse;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.dto.ModerationStatsResponse;
import com.careertuner.community.moderation.mapper.CommentAiResultMapper;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class AdminModerationService {

    private final PostAiResultMapper aiResultMapper;
    private final CommentAiResultMapper commentAiResultMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final PostModerationService moderationService;
    private final ObjectMapper objectMapper;

    public AdminModerationService(PostAiResultMapper aiResultMapper,
                                  CommentAiResultMapper commentAiResultMapper,
                                  CommunityPostMapper postMapper,
                                  CommunityCommentMapper commentMapper,
                                  PostModerationService moderationService,
                                  ObjectMapper objectMapper) {
        this.aiResultMapper = aiResultMapper;
        this.commentAiResultMapper = commentAiResultMapper;
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
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

    // ── 댓글 검열 관리 (게시글 메서드 복제, 대상만 댓글) ──

    public ModerationPageResponse getCommentList(ModerationListRequest request) {
        List<ModerationView> views = commentAiResultMapper.findCommentModerationList(
                request.status(), request.toxic(), request.offset(), request.size());
        int total = commentAiResultMapper.countCommentModerationList(request.status(), request.toxic());

        List<ModerationItemResponse> items = views.stream()
                .map(this::toItemResponse)
                .toList();

        return new ModerationPageResponse(
                items, total, request.page(), request.size(),
                request.offset() + request.size() < total
        );
    }

    public ModerationDetailResponse getCommentDetail(Long commentId) {
        ModerationView view = commentAiResultMapper.findCommentModerationDetail(commentId);
        if (view == null) {
            throw new IllegalArgumentException("댓글 검열 결과를 찾을 수 없습니다: commentId=" + commentId);
        }
        return toDetailResponse(view);
    }

    /** 댓글 복원 HIDDEN→PUBLISHED. 경계 통과 시에만 comment_count +1. */
    public void restoreComment(Long commentId) {
        CommunityComment comment = commentMapper.findById(commentId);
        if (comment == null) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다: commentId=" + commentId);
        }
        if (commentMapper.restoreCommentIfHidden(commentId) > 0) {
            postMapper.incrementCommentCount(comment.getPostId());
        }
        moderationService.sendCommentRestoredNotification(comment);
    }

    /** 댓글 확정 삭제. PUBLISHED였을 때만 -1(이미 HIDDEN이면 DELETED 전환만, 이중감소 없음). */
    public void deleteComment(Long commentId) {
        CommunityComment comment = commentMapper.findById(commentId);
        if (comment == null) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다: commentId=" + commentId);
        }
        if (commentMapper.deleteCommentIfPublished(commentId) > 0) {
            postMapper.decrementCommentCount(comment.getPostId());
        } else {
            commentMapper.updateStatus(commentId, CommentStatus.DELETED.name());
        }
        moderationService.sendCommentDeletedNotification(comment);
    }

    public ModerationStatsResponse getCommentStats() {
        List<Map<String, Object>> rows = commentAiResultMapper.countCommentByAiCategory();
        List<ModerationStatsResponse.CategoryCount> categories = rows.stream()
                .map(row -> new ModerationStatsResponse.CategoryCount(
                        String.valueOf(row.get("category")),
                        ((Number) row.get("cnt")).intValue()
                ))
                .toList();
        int total = categories.stream().mapToInt(ModerationStatsResponse.CategoryCount::count).sum();
        return new ModerationStatsResponse(categories, total);
    }

    /** 카테고리 코드를 한글 라벨로 변환. enum이 아닌 값(댓글 등)은 그대로 둔다. */
    private static String categoryLabel(String raw) {
        if (raw == null) return null;
        try {
            return PostCategory.valueOf(raw).getLabel();
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    private ModerationItemResponse toItemResponse(ModerationView view) {
        ModerationResult result = parseResult(view.getResultJson());
        return new ModerationItemResponse(
                view.getPostId(),
                view.getTitle(),
                view.isAnonymous() ? "익명" : view.getAuthorName(),
                categoryLabel(view.getPostCategory()),
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
                categoryLabel(view.getPostCategory()),
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
