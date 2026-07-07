package com.careertuner.community.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.CommentReaction;
import com.careertuner.community.domain.CommunityComment;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostReaction;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.domain.ReactionType;
import com.careertuner.community.dto.PostReactorResponse;
import com.careertuner.community.dto.ToggleReactionRequest;
import com.careertuner.community.dto.ToggleReactionResponse;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.ReactionMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 리액션 토글 — 기존 견고 패턴(존재 확인 → 토글, UNIQUE 최종 방어, 동시성 충돌 흡수, best-effort 알림)을
 * 축(axis) 모델로 확장한다. 같은 축의 반대 리액션 클릭 시 교체, 같은 것 재클릭 시 취소.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReactionServiceImpl implements ReactionService {

    private final ReactionMapper reactionMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ToggleReactionResponse toggleReaction(ToggleReactionRequest request, Long userId) {
        return switch (request.targetType()) {
            case POST -> togglePostReaction(request.targetId(), request.reactionType(), request.isAnonymous(), userId);
            case COMMENT -> toggleCommentReaction(request.targetId(), request.reactionType(), request.isAnonymous(), userId);
        };
    }

    private ToggleReactionResponse togglePostReaction(Long postId, ReactionType type, boolean anonymous, Long userId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        // 자기 글 평가 차단 (즐겨찾기는 허용)
        if (type != ReactionType.BOOKMARK && userId.equals(post.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자신의 게시글에는 반응할 수 없습니다.");
        }

        PostReaction existing = reactionMapper.findPostReactionByAxis(userId, postId, type.axis().name());
        if (existing != null && type.name().equals(existing.getReactionType())) {
            // 같은 것 재클릭 → 취소(토글). 취소 시 알림은 발행하지 않는다(기존 패턴).
            // dev(#238): affected-rows 검증 — 동시 취소 충돌이면 카운트 재감소 없이 흡수.
            int deleted = reactionMapper.deletePostReaction(userId, postId, type.name());
            if (deleted <= 0) {
                log.info("게시글 리액션 동시 취소 충돌 흡수 postId={} userId={} type={}", postId, userId, type);
                return postResponse(postId, type, false);
            }
            reactionMapper.adjustPostReactionCount(postId, type.name(), -1);
            log.info("게시글 리액션 취소 postId={} userId={} type={}", postId, userId, type);
            return postResponse(postId, type, false);
        }
        if (existing != null) {
            // 같은 축의 반대 리액션 → 교체(기존 행 삭제 후 새 행 등록)
            reactionMapper.deletePostReaction(userId, postId, existing.getReactionType());
            reactionMapper.adjustPostReactionCount(postId, existing.getReactionType(), -1);
        }
        try {
            int inserted = reactionMapper.insertPostReaction(PostReaction.builder()
                    .userId(userId).postId(postId)
                    .reactionType(type.name()).axis(type.axis().name())
                    .anonymous(anonymous)
                    .build());
            if (inserted <= 0) {
                // dev(#238): affected-rows 검증 — 등록 실패 시 충돌로 처리.
                throw new BusinessException(ErrorCode.CONFLICT, "게시글 리액션 등록에 실패했습니다.");
            }
        } catch (DuplicateKeyException e) {
            // 동시 토글 충돌: 이미 다른 트랜잭션이 같은 축 리액션을 등록함. 카운트 재증가 없이 흡수.
            log.info("게시글 리액션 동시 등록 충돌 흡수 postId={} userId={} type={}", postId, userId, type);
            return postResponse(postId, type, true);
        }
        reactionMapper.adjustPostReactionCount(postId, type.name(), 1);
        // '신규 등록'에만 알림(취소·동시 충돌 흡수 경로는 위에서 이미 return). 본인 글 평가는 위에서 차단,
        // 본인 글 즐겨찾기는 수신자=행위자라 스킵.
        if (!userId.equals(post.getUserId())) {
            notifyPostReaction(post, type, anonymous, userId);
        }
        log.info("게시글 리액션 등록 postId={} userId={} type={} anonymous={}", postId, userId, type, anonymous);
        return postResponse(postId, type, true);
    }

    private ToggleReactionResponse toggleCommentReaction(Long commentId, ReactionType type, boolean anonymous, Long userId) {
        if (type == ReactionType.BOOKMARK) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "댓글에는 즐겨찾기를 할 수 없습니다.");
        }
        CommunityComment comment = commentMapper.findById(commentId);
        if (comment == null || !PostStatus.PUBLISHED.name().equals(comment.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (userId.equals(comment.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자신의 댓글에는 반응할 수 없습니다.");
        }

        CommentReaction existing = reactionMapper.findCommentReactionByAxis(userId, commentId, type.axis().name());
        if (existing != null && type.name().equals(existing.getReactionType())) {
            // dev(#238): affected-rows 검증 — 동시 취소 충돌이면 카운트 재감소 없이 흡수.
            int deleted = reactionMapper.deleteCommentReaction(userId, commentId, type.name());
            if (deleted <= 0) {
                log.info("댓글 리액션 동시 취소 충돌 흡수 commentId={} userId={} type={}", commentId, userId, type);
                return commentResponse(commentId, type, false);
            }
            reactionMapper.adjustCommentReactionCount(commentId, type.name(), -1);
            log.info("댓글 리액션 취소 commentId={} userId={} type={}", commentId, userId, type);
            return commentResponse(commentId, type, false);
        }
        if (existing != null) {
            reactionMapper.deleteCommentReaction(userId, commentId, existing.getReactionType());
            reactionMapper.adjustCommentReactionCount(commentId, existing.getReactionType(), -1);
        }
        try {
            reactionMapper.insertCommentReaction(CommentReaction.builder()
                    .userId(userId).commentId(commentId)
                    .reactionType(type.name()).axis(type.axis().name())
                    .anonymous(anonymous)
                    .build());
        } catch (DuplicateKeyException e) {
            log.info("댓글 리액션 동시 등록 충돌 흡수 commentId={} userId={} type={}", commentId, userId, type);
            return commentResponse(commentId, type, true);
        }
        reactionMapper.adjustCommentReactionCount(commentId, type.name(), 1);
        notifyCommentReaction(comment, type, anonymous, userId);
        log.info("댓글 리액션 등록 commentId={} userId={} type={} anonymous={}", commentId, userId, type, anonymous);
        return commentResponse(commentId, type, true);
    }

    @Override
    public List<PostReactorResponse> getPostReactors(Long postId, Long viewerId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        List<PostReactorResponse> result = new ArrayList<>();
        for (PostReaction r : reactionMapper.findPostReactors(postId, viewerId)) {
            boolean mine = viewerId != null && viewerId.equals(r.getUserId());
            // 익명 리액션은 쿼리에서 본인 것만 남는다 — 표시명도 익명 유지(본인은 mine 으로 구분)
            result.add(new PostReactorResponse(
                    r.getReactionType(),
                    r.isAnonymous() ? null : r.getUserId(),
                    r.isAnonymous() ? "익명" : userNameOf(r),
                    r.isAnonymous(),
                    mine,
                    r.getCreatedAt()));
        }
        return result;
    }

    private static String userNameOf(PostReaction r) {
        // findPostReactors 가 users JOIN 별칭(userName)으로 채운다 — 도메인에 없는 필드라 리플렉션 대신 예외 없이 처리
        return r.getUserName() != null ? r.getUserName() : "사용자";
    }

    /* ── 응답 조립 (토글 후 카운트 재조회 — 응답 기반 UI 갱신 계약) ── */

    private ToggleReactionResponse postResponse(Long postId, ReactionType type, boolean active) {
        CommunityPost fresh = postMapper.findById(postId);
        return new ToggleReactionResponse(active, type.name(), new ToggleReactionResponse.CountsDto(
                fresh.getLikeCount(), fresh.getDislikeCount(),
                fresh.getRecommendCount(), fresh.getDisrecommendCount(),
                fresh.getBookmarkCount(), fresh.getScrapCount()));
    }

    private ToggleReactionResponse commentResponse(Long commentId, ReactionType type, boolean active) {
        CommunityComment fresh = commentMapper.findById(commentId);
        return new ToggleReactionResponse(active, type.name(), new ToggleReactionResponse.CountsDto(
                fresh.getLikeCount(), fresh.getDislikeCount(),
                fresh.getRecommendCount(), fresh.getDisrecommendCount(),
                0, 0));
    }

    /* ── 알림 (best-effort — 발행 실패가 리액션 처리를 깨지 않는다) ── */

    /** 게시글 리액션 알림 type: LIKE / POST_DISLIKE / POST_RECOMMEND / POST_DISRECOMMEND / POST_BOOKMARK (+_ANON). */
    private void notifyPostReaction(CommunityPost post, ReactionType type, boolean anonymous, Long actorId) {
        String base = switch (type) {
            case LIKE -> "LIKE";
            case DISLIKE -> "POST_DISLIKE";
            case RECOMMEND -> "POST_RECOMMEND";
            case DISRECOMMEND -> "POST_DISRECOMMEND";
            case BOOKMARK -> "POST_BOOKMARK";
        };
        String label = reactionLabel(type);
        // 익명이면 actorId 는 저장하되 title/message 에 이름·식별 정보를 넣지 않는다(클라이언트도 _ANON 타입은 익명 표시)
        String title = anonymous
                ? "누군가 게시글을 " + label + "했습니다."
                : "게시글에 " + label + "이(가) 달렸습니다.";
        String message = "'" + truncate(post.getTitle(), 30) + "' 게시글에 "
                + (anonymous ? "익명 " : "") + label + "이(가) 등록되었습니다.";
        notifyQuietly(Notification.builder()
                .userId(post.getUserId())
                .actorId(actorId)
                .type(anonymous ? base + "_ANON" : base)
                .targetType("POST")
                .targetId(post.getId())
                .title(title)
                .message(message)
                .link("/community/posts/" + post.getId())
                .build());
    }

    /** 댓글 리액션 알림 type: COMMENT_LIKE / COMMENT_DISLIKE / COMMENT_RECOMMEND / COMMENT_DISRECOMMEND (+_ANON). */
    private void notifyCommentReaction(CommunityComment comment, ReactionType type, boolean anonymous, Long actorId) {
        String base = switch (type) {
            case LIKE -> "COMMENT_LIKE";
            case DISLIKE -> "COMMENT_DISLIKE";
            case RECOMMEND -> "COMMENT_RECOMMEND";
            case DISRECOMMEND -> "COMMENT_DISRECOMMEND";
            case BOOKMARK -> throw new IllegalStateException("댓글 즐겨찾기는 존재하지 않는다");
        };
        String label = reactionLabel(type);
        String title = anonymous
                ? "누군가 댓글을 " + label + "했습니다."
                : "댓글에 " + label + "이(가) 달렸습니다.";
        notifyQuietly(Notification.builder()
                .userId(comment.getUserId())
                .actorId(actorId)
                .type(anonymous ? base + "_ANON" : base)
                .targetType("COMMENT")
                .targetId(comment.getId())
                .title(title)
                .message("'" + truncate(comment.getContent(), 30) + "' 댓글에 "
                        + (anonymous ? "익명 " : "") + label + "이(가) 등록되었습니다.")
                .link("/community/posts/" + comment.getPostId())
                .build());
    }

    private static String reactionLabel(ReactionType type) {
        return switch (type) {
            case LIKE -> "좋아요";
            case DISLIKE -> "싫어요";
            case RECOMMEND -> "추천";
            case DISRECOMMEND -> "비추천";
            case BOOKMARK -> "즐겨찾기";
        };
    }

    /** 알림 발행 best-effort — 개인 차단·수신 설정 필터는 NotificationService.notify 가 처리한다. */
    private void notifyQuietly(Notification notification) {
        try {
            notificationService.notify(notification);
        } catch (Exception e) {
            log.error("리액션 알림 발행 실패: type={} targetId={}", notification.getType(), notification.getTargetId(), e);
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }
}
