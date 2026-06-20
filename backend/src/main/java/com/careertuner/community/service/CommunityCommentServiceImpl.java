package com.careertuner.community.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Map<Long, String> anonLabels = buildAnonLabels(comments);
        List<CommentResponse> result = new ArrayList<>(comments.size());
        for (CommunityComment c : comments) {
            // 멘션 표시명은 저장된 mention_user_id 를 현재 익명번호로 동적 변환(번호 밀림에 안전)
            String mentionLabel = c.getMentionUserId() != null
                    ? anonLabels.get(c.getMentionUserId())
                    : null;
            result.add(toResponse(c, post.getUserId(), currentUserId, displayName(c, anonLabels), mentionLabel));
        }
        return result;
    }

    /**
     * 글 단위 익명 번호 부여(익명1, 익명2…) — 작성 시간 순으로 처음 등장한 사용자부터.
     * 익명이라도 서로 구분 가능해야 답글 멘션(@익명N)이 의미를 가진다.
     */
    private Map<Long, String> buildAnonLabels(List<CommunityComment> orderedComments) {
        Map<Long, String> labels = new HashMap<>();
        for (CommunityComment c : orderedComments) {
            if (c.isAnonymous()) {
                labels.computeIfAbsent(c.getUserId(), k -> "익명" + (labels.size() + 1));
            }
        }
        return labels;
    }

    private String displayName(CommunityComment c, Map<Long, String> anonLabels) {
        return c.isAnonymous() ? anonLabels.getOrDefault(c.getUserId(), "익명") : c.getUserName();
    }

    @Override
    @Transactional
    public CommentResponse createComment(Long postId, CreateCommentRequest request, Long userId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }

        // 디시인사이드식 2단계: 답글의 답글도 깊어지지 않고 최상위 댓글 그룹에 붙인다.
        //  - parentId        = 최상위 댓글(루트 그룹). 삭제에 강건.
        //  - mentionUserId   = 답글이 실제로 가리키는 대상 사용자(불변 참조). 표시명은 읽을 때 동적 렌더.
        // 클라이언트는 클릭한 댓글 id만 보내고, 정규화·멘션 산출은 서버가 한다.
        Long effectiveParentId = null;
        Long mentionUserId = null;
        if (request.parentId() != null) {
            CommunityComment target = commentMapper.findById(request.parentId());
            if (target == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "부모 댓글을 찾을 수 없습니다.");
            }
            if (!target.getPostId().equals(postId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "다른 게시글의 댓글에는 답글을 달 수 없습니다.");
            }
            effectiveParentId = target.getParentId() != null ? target.getParentId() : target.getId();
            // 대댓글에 단 답글이면 대상 사용자를 멘션으로 기록.
            // 단 루트 직속 답글이거나 자기 자신에게 다는 답글이면 멘션 불필요.
            if (target.getParentId() != null && !userId.equals(target.getUserId())) {
                mentionUserId = target.getUserId();
            }
        }

        CommunityComment comment = CommunityComment.builder()
                .postId(postId)
                .userId(userId)
                .parentId(effectiveParentId)
                .mentionUserId(mentionUserId)
                .content(request.content())
                .anonymous(request.anonymous() == null || request.anonymous())
                .status(CommentStatus.PUBLISHED.name())
                .build();
        commentMapper.insert(comment);
        postMapper.incrementCommentCount(postId);

        log.info("댓글 작성 postId={} commentId={}", postId, comment.getId());
        // 작성 직후 응답은 프론트가 곧바로 목록을 재조회하므로 라벨은 단순값으로 둔다.
        return toResponse(comment, post.getUserId(), userId,
                comment.isAnonymous() ? "익명" : comment.getUserName(), null);
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

    private CommentResponse toResponse(CommunityComment c, Long postAuthorId, Long currentUserId,
                                       String authorName, String mentionLabel) {
        boolean liked = currentUserId != null
                && reactionMapper.findCommentReaction(currentUserId, c.getId(), "LIKE") != null;
        return new CommentResponse(
                c.getId(),
                c.getPostId(),
                c.getParentId(),
                mentionLabel,
                new PostListResponse.AuthorDto(
                        c.isAnonymous() ? null : c.getUserId(),
                        authorName,
                        c.isAnonymous()),
                c.getContent(),
                c.getLikeCount(),
                c.getUserId().equals(postAuthorId),
                c.getCreatedAt(),
                liked);
    }
}
