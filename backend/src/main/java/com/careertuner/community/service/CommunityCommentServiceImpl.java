package com.careertuner.community.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.CommunityComment;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.CommentStatus;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.dto.CommentResponse;
import com.careertuner.community.dto.CreateCommentRequest;
import com.careertuner.community.dto.PostListResponse;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.ReactionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityCommentServiceImpl implements CommunityCommentService {

    private final CommunityCommentMapper commentMapper;
    private final CommunityPostMapper postMapper;
    private final ReactionMapper reactionMapper;

    @Override
    public List<CommentResponse> getComments(Long postId, Long currentUserId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        List<CommunityComment> comments = commentMapper.findByPostId(postId, CommentStatus.PUBLISHED.name());
        return comments.stream()
                .map(c -> toResponse(c, post.getUserId(), currentUserId))
                .toList();
    }

    @Override
    @Transactional
    public CommentResponse createComment(Long postId, CreateCommentRequest request, Long userId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }

        // 대댓글 1depth 검증
        if (request.parentId() != null) {
            CommunityComment parent = commentMapper.findById(request.parentId());
            if (parent == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "부모 댓글을 찾을 수 없습니다.");
            }
            if (parent.getParentId() != null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "대댓글에는 답글을 달 수 없습니다.");
            }
        }

        CommunityComment comment = CommunityComment.builder()
                .postId(postId)
                .userId(userId)
                .parentId(request.parentId())
                .content(request.content())
                .anonymous(true)
                .status(CommentStatus.PUBLISHED.name())
                .build();
        commentMapper.insert(comment);
        postMapper.incrementCommentCount(postId);

        log.info("댓글 작성 postId={} commentId={}", postId, comment.getId());
        return toResponse(comment, post.getUserId(), userId);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        CommunityComment comment = commentMapper.findById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 댓글만 삭제할 수 있습니다.");
        }
        commentMapper.updateStatus(commentId, CommentStatus.DELETED.name());
        postMapper.decrementCommentCount(comment.getPostId());
    }

    private CommentResponse toResponse(CommunityComment c, Long postAuthorId, Long currentUserId) {
        boolean liked = currentUserId != null
                && reactionMapper.findCommentReaction(currentUserId, c.getId(), "LIKE") != null;
        return new CommentResponse(
                c.getId(),
                c.getPostId(),
                new PostListResponse.AuthorDto(
                        c.isAnonymous() ? null : c.getUserId(),
                        c.isAnonymous() ? "익명" : c.getUserName(),
                        c.isAnonymous()),
                c.getContent(),
                c.getLikeCount(),
                c.getUserId().equals(postAuthorId),
                c.getCreatedAt(),
                liked);
    }
}
