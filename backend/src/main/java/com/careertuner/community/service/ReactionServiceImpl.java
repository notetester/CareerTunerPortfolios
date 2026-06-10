package com.careertuner.community.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.CommentReaction;
import com.careertuner.community.domain.PostReaction;
import com.careertuner.community.domain.ReactionType;
import com.careertuner.community.dto.ToggleReactionRequest;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.ReactionMapper;

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

    @Override
    @Transactional
    public boolean toggleReaction(ToggleReactionRequest request, Long userId) {
        return switch (request.targetType()) {
            case POST -> togglePostReaction(request.targetId(), request.reactionType(), userId);
            case COMMENT -> toggleCommentReaction(request.targetId(), request.reactionType(), userId);
        };
    }

    private boolean togglePostReaction(Long postId, ReactionType type, Long userId) {
        PostReaction existing = reactionMapper.findPostReaction(userId, postId, type.name());
        if (existing != null) {
            reactionMapper.deletePostReaction(userId, postId, type.name());
            if (type == ReactionType.LIKE) postMapper.decrementLikeCount(postId);
            else postMapper.decrementBookmarkCount(postId);
            log.info("게시글 리액션 취소 postId={} userId={} type={}", postId, userId, type);
            return false;
        }
        reactionMapper.insertPostReaction(PostReaction.builder()
                .userId(userId).postId(postId).reactionType(type.name()).build());
        if (type == ReactionType.LIKE) postMapper.incrementLikeCount(postId);
        else postMapper.incrementBookmarkCount(postId);
        log.info("게시글 리액션 등록 postId={} userId={} type={}", postId, userId, type);
        return true;
    }

    private boolean toggleCommentReaction(Long commentId, ReactionType type, Long userId) {
        if (type == ReactionType.BOOKMARK) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "댓글에는 북마크를 할 수 없습니다.");
        }
        CommentReaction existing = reactionMapper.findCommentReaction(userId, commentId, type.name());
        if (existing != null) {
            reactionMapper.deleteCommentReaction(userId, commentId, type.name());
            commentMapper.decrementLikeCount(commentId);
            log.info("댓글 리액션 취소 commentId={} userId={}", commentId, userId);
            return false;
        }
        reactionMapper.insertCommentReaction(CommentReaction.builder()
                .userId(userId).commentId(commentId).reactionType(type.name()).build());
        commentMapper.incrementLikeCount(commentId);
        log.info("댓글 리액션 등록 commentId={} userId={}", commentId, userId);
        return true;
    }
}
