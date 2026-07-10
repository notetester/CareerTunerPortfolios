package com.careertuner.community.moderation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.jsoup.Jsoup;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.CommentStatus;
import com.careertuner.community.domain.CommunityComment;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostCategory;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.ModerationView;
import com.careertuner.community.moderation.domain.ModerationReviewAction;
import com.careertuner.community.moderation.domain.ModerationReviewQueueView;
import com.careertuner.community.moderation.domain.PostAiResult;
import com.careertuner.community.moderation.dto.ModerationDetailResponse;
import com.careertuner.community.moderation.dto.ModerationItemResponse;
import com.careertuner.community.moderation.dto.ModerationListRequest;
import com.careertuner.community.moderation.dto.ModerationPageResponse;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.dto.ModerationReviewQueueItemResponse;
import com.careertuner.community.moderation.dto.ModerationReviewQueuePageResponse;
import com.careertuner.community.moderation.dto.ModerationStatsResponse;
import com.careertuner.community.moderation.mapper.CommentAiResultMapper;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminModerationService {

    private final PostAiResultMapper aiResultMapper;
    private final CommentAiResultMapper commentAiResultMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final PostModerationService moderationService;
    private final ModerationSettingService settingService;
    private final ObjectMapper objectMapper;

    public AdminModerationService(PostAiResultMapper aiResultMapper,
                                  CommentAiResultMapper commentAiResultMapper,
                                  CommunityPostMapper postMapper,
                                  CommunityCommentMapper commentMapper,
                                  PostModerationService moderationService,
                                  ModerationSettingService settingService,
                                  ObjectMapper objectMapper) {
        this.aiResultMapper = aiResultMapper;
        this.commentAiResultMapper = commentAiResultMapper;
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.moderationService = moderationService;
        this.settingService = settingService;
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

    @Transactional(readOnly = true)
    public ModerationReviewQueuePageResponse getReviewQueue(int page, int size) {
        ModerationListRequest paging = new ModerationListRequest(null, null, page, size);
        double hideThreshold = settingService.getHideThreshold();
        List<ModerationReviewQueueItemResponse> items = aiResultMapper.findReviewQueue(
                        hideThreshold, paging.offset(), paging.size()).stream()
                .map(this::toReviewQueueItem)
                .toList();
        int total = aiResultMapper.countReviewQueue(hideThreshold);
        return new ModerationReviewQueuePageResponse(
                items, total, paging.page(), paging.size(), paging.offset() + paging.size() < total);
    }

    /**
     * 경계 검열 결과를 한 번만 결정한다. 같은 action 재시도는 성공으로 끝내고,
     * 다른 action 재시도나 더 이상 큐 조건을 만족하지 않는 요청은 충돌로 거절한다.
     */
    @Transactional
    public void decideReviewQueue(Long reviewerId, Long postId, String rawAction) {
        ModerationReviewAction action = parseReviewAction(rawAction);
        double hideThreshold = settingService.getHideThreshold();
        int acquired = aiResultMapper.recordReviewAction(postId, action.name(), reviewerId, hideThreshold);
        if (acquired == 0) {
            String existing = aiResultMapper.findReviewAction(postId);
            if (action.name().equals(existing)) {
                return;
            }
            if (existing != null) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "이미 " + existing + " 결정이 완료된 검토 항목입니다.");
            }
            throw new BusinessException(ErrorCode.CONFLICT,
                    "더 이상 검토 대기 조건을 만족하지 않는 게시글입니다.");
        }

        if (action == ModerationReviewAction.HIDE) {
            CommunityPost post = postMapper.findById(postId);
            if (post == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
            }
            if (postMapper.hideIfPublished(postId) == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "이미 게시 상태가 변경된 게시글입니다.");
            }
            moderationService.sendReviewHiddenNotification(post);
        }
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
                result != null && result.confidence() != null ? result.confidence() : 0,
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
                result != null && result.confidence() != null ? result.confidence() : 0,
                view.getModel(),
                view.getAttemptCount(),
                view.getCreatedAt(),
                view.getCompletedAt(),
                loadImageResults(view.getPostId())
        );
    }

    private ModerationReviewQueueItemResponse toReviewQueueItem(ModerationReviewQueueView view) {
        String content = view.getContent() == null ? "" : Jsoup.parse(view.getContent()).text();
        String preview = content.length() > 240 ? content.substring(0, 240) + "…" : content;
        return new ModerationReviewQueueItemResponse(
                view.getPostId(),
                view.getTitle(),
                preview,
                view.isAnonymous() ? "익명" : view.getAuthorName(),
                categoryLabel(view.getPostCategory()),
                view.getAiCategory(),
                view.getConfidence(),
                view.getCreatedAt(),
                view.getCompletedAt()
        );
    }

    private static ModerationReviewAction parseReviewAction(String rawAction) {
        if (rawAction == null || rawAction.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "action은 HIDE 또는 KEEP이어야 합니다.");
        }
        try {
            return ModerationReviewAction.valueOf(rawAction.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "action은 HIDE 또는 KEEP이어야 합니다.");
        }
    }

    /** 본문 이미지 검열(IMAGE_MODERATION) 결과를 관리자 상세용 목록으로 파싱. 없으면 빈 목록(best-effort). */
    private List<ModerationDetailResponse.ImageModerationItem> loadImageResults(Long postId) {
        List<ModerationDetailResponse.ImageModerationItem> items = new ArrayList<>();
        try {
            PostAiResult result = aiResultMapper.findByPostIdAndTaskType(postId, AiTaskType.IMAGE_MODERATION);
            if (result == null || result.getResultJson() == null) {
                return items;
            }
            JsonNode images = objectMapper.readTree(result.getResultJson()).path("images");
            if (images.isArray()) {
                for (JsonNode img : images) {
                    JsonNode conf = img.path("confidence");
                    items.add(new ModerationDetailResponse.ImageModerationItem(
                            textOrNull(img, "url"),
                            textOrNull(img, "category"),
                            conf.isNumber() ? conf.asDouble() : null,
                            textOrNull(img, "action")));
                }
            }
        } catch (Exception e) {
            // 이미지 결과 없거나 파싱 실패 → 빈 목록으로 조용히 넘어간다(텍스트 상세는 그대로 노출).
        }
        return items;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
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
