package com.careertuner.community.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.CommunityComment;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.CommunitySubscriptionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 글/댓글 구독 토글 — 팬아웃(새 댓글 → POST_WATCH_COMMENT, 새 답글 → COMMENT_WATCH_REPLY)은
 * 댓글 생성 훅(CommunityCommentServiceImpl)에서 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunitySubscriptionServiceImpl implements CommunitySubscriptionService {

    private final CommunitySubscriptionMapper subscriptionMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;

    @Override
    @Transactional
    public boolean togglePostSubscription(Long postId, Long userId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        if (subscriptionMapper.existsPostSubscription(userId, postId)) {
            subscriptionMapper.deletePostSubscription(userId, postId);
            log.info("글 구독 해지 postId={} userId={}", postId, userId);
            return false;
        }
        try {
            subscriptionMapper.insertPostSubscription(userId, postId);
        } catch (DuplicateKeyException e) {
            // 동시 토글 충돌 흡수 — 이미 구독 상태
            log.info("글 구독 동시 등록 충돌 흡수 postId={} userId={}", postId, userId);
        }
        log.info("글 구독 등록 postId={} userId={}", postId, userId);
        return true;
    }

    @Override
    @Transactional
    public boolean toggleCommentSubscription(Long commentId, Long userId) {
        CommunityComment comment = commentMapper.findById(commentId);
        if (comment == null || !PostStatus.PUBLISHED.name().equals(comment.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        if (subscriptionMapper.existsCommentSubscription(userId, commentId)) {
            subscriptionMapper.deleteCommentSubscription(userId, commentId);
            log.info("댓글 구독 해지 commentId={} userId={}", commentId, userId);
            return false;
        }
        try {
            subscriptionMapper.insertCommentSubscription(userId, commentId);
        } catch (DuplicateKeyException e) {
            log.info("댓글 구독 동시 등록 충돌 흡수 commentId={} userId={}", commentId, userId);
        }
        log.info("댓글 구독 등록 commentId={} userId={}", commentId, userId);
        return true;
    }
}
