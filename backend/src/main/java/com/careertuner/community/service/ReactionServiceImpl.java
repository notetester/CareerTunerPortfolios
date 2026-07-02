package com.careertuner.community.service;

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
import com.careertuner.community.dto.ToggleReactionRequest;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.ReactionMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    public boolean toggleReaction(ToggleReactionRequest request, Long userId) {
        return switch (request.targetType()) {
            case POST -> togglePostReaction(request.targetId(), request.reactionType(), userId);
            case COMMENT -> toggleCommentReaction(request.targetId(), request.reactionType(), userId);
        };
    }

    private boolean togglePostReaction(Long postId, ReactionType type, Long userId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        // self-like 차단 (북마크는 허용)
        if (type == ReactionType.LIKE && userId.equals(post.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자신의 게시글에는 좋아요를 할 수 없습니다.");
        }
        PostReaction existing = reactionMapper.findPostReaction(userId, postId, type.name());
        if (existing != null) {
            reactionMapper.deletePostReaction(userId, postId, type.name());
            if (type == ReactionType.LIKE) postMapper.decrementLikeCount(postId);
            else postMapper.decrementBookmarkCount(postId);
            log.info("게시글 리액션 취소 postId={} userId={} type={}", postId, userId, type);
            return false;
        }
        try {
            reactionMapper.insertPostReaction(PostReaction.builder()
                    .userId(userId).postId(postId).reactionType(type.name()).build());
        } catch (DuplicateKeyException e) {
            // 동시 토글 충돌: 이미 다른 트랜잭션이 동일 리액션을 등록함. 카운트 재증가 없이 흡수.
            log.info("게시글 리액션 동시 등록 충돌 흡수 postId={} userId={} type={}", postId, userId, type);
            return true;
        }
        if (type == ReactionType.LIKE) postMapper.incrementLikeCount(postId);
        else postMapper.incrementBookmarkCount(postId);
        // 좋아요 '신규 등록'에만 알림 발행(취소·동시 등록 흡수 경로는 위에서 이미 return).
        // self-like 는 위에서 차단되므로 본인 알림은 발생하지 않는다.
        if (type == ReactionType.LIKE) {
            notifyPostLiked(post, userId);
        }
        log.info("게시글 리액션 등록 postId={} userId={} type={}", postId, userId, type);
        return true;
    }

    /**
     * 게시글 좋아요 알림 — 발행 실패가 리액션 처리를 깨지 않도록 best-effort.
     * 링크는 검열 알림(PostModerationService)과 동일한 게시글 상세 패턴을 쓴다.
     */
    private void notifyPostLiked(CommunityPost post, Long actorId) {
        try {
            notificationService.notify(Notification.builder()
                    .userId(post.getUserId())
                    .actorId(actorId)
                    .type("LIKE")
                    .targetType("POST")
                    .targetId(post.getId())
                    .title("게시글에 좋아요가 달렸습니다.")
                    .message("'" + truncate(post.getTitle(), 30) + "' 게시글에 좋아요를 남겼습니다.")
                    .link("/community/posts/" + post.getId())
                    .build());
        } catch (Exception e) {
            log.error("좋아요 알림 발행 실패: postId={} actorId={}", post.getId(), actorId, e);
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    private boolean toggleCommentReaction(Long commentId, ReactionType type, Long userId) {
        if (type == ReactionType.BOOKMARK) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "댓글에는 북마크를 할 수 없습니다.");
        }
        CommunityComment comment = commentMapper.findById(commentId);
        if (comment == null || !PostStatus.PUBLISHED.name().equals(comment.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        // self-like 차단 (댓글 리액션은 LIKE 뿐)
        if (userId.equals(comment.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자신의 댓글에는 좋아요를 할 수 없습니다.");
        }
        CommentReaction existing = reactionMapper.findCommentReaction(userId, commentId, type.name());
        if (existing != null) {
            reactionMapper.deleteCommentReaction(userId, commentId, type.name());
            commentMapper.decrementLikeCount(commentId);
            log.info("댓글 리액션 취소 commentId={} userId={}", commentId, userId);
            return false;
        }
        try {
            reactionMapper.insertCommentReaction(CommentReaction.builder()
                    .userId(userId).commentId(commentId).reactionType(type.name()).build());
        } catch (DuplicateKeyException e) {
            // 동시 토글 충돌: 이미 다른 트랜잭션이 동일 리액션을 등록함. 카운트 재증가 없이 흡수.
            log.info("댓글 리액션 동시 등록 충돌 흡수 commentId={} userId={}", commentId, userId);
            return true;
        }
        commentMapper.incrementLikeCount(commentId);
        log.info("댓글 리액션 등록 commentId={} userId={}", commentId, userId);
        return true;
    }
}
