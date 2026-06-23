package com.careertuner.community.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
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
import com.careertuner.community.moderation.event.CommentModerationRequiredEvent;

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
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<CommentResponse> getComments(Long postId, Long currentUserId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        // soft-delete(자삭=DELETED, 관리자=HIDDEN)는 row를 지우지 않으므로 전체를 가져온다.
        //  - 익명N 앵커 보존(앞 사용자 자삭 시 뒷 번호 밀림 방지 → mention_user_id 라벨 불변)
        //  - 살아있는 답글을 가진 삭제/숨김 노드를 tombstone으로 표시하기 위한 입력
        List<CommunityComment> comments = commentMapper.findAllByPostId(postId);

        Map<Long, String> anonLabels = buildAnonLabels(comments);
        // 멘션 대상이 비익명이면 익명라벨이 없으므로 실명으로 폴백(작성자 표시 경로와 동일하게 비대칭 제거).
        // 멘션 대상은 반드시 이 글에 댓글을 단 사용자이므로 전체 목록에서 이름을 찾을 수 있다.
        Map<Long, String> userNames = new HashMap<>();
        for (CommunityComment c : comments) {
            userNames.putIfAbsent(c.getUserId(), c.getUserName());
        }

        // 표시 대상 = PUBLISHED 전체 + (살아있는 자손을 가진 삭제/숨김 노드)
        Set<Long> renderable = computeRenderable(comments);

        List<CommentResponse> result = new ArrayList<>(comments.size());
        for (CommunityComment c : comments) {
            if (!renderable.contains(c.getId())) {
                continue; // 살아있는 자손 없는 삭제/숨김 leaf — 렌더 제외(단 위 번호 계산엔 이미 포함됨)
            }
            if (!CommentStatus.PUBLISHED.name().equals(c.getStatus())) {
                result.add(tombstone(c)); // 삭제/숨김이지만 자손이 살아있음 → 골격 유지용 tombstone
                continue;
            }
            // 멘션 표시명은 저장된 mention_user_id 를 현재 익명번호로 동적 변환(번호 밀림에 안전).
            // 익명라벨이 없으면(=대상이 비익명) 실명으로 폴백한다.
            String mentionLabel = null;
            if (c.getMentionUserId() != null) {
                mentionLabel = anonLabels.get(c.getMentionUserId());
                if (mentionLabel == null) {
                    mentionLabel = userNames.get(c.getMentionUserId());
                }
            }
            result.add(toResponse(c, post.getUserId(), currentUserId,
                    displayName(c, anonLabels), mentionLabel, false));
        }
        return result;
    }

    /**
     * 글 단위 익명 번호 부여(익명1, 익명2…) — 작성 시간 순으로 처음 등장한 사용자부터.
     * 익명이라도 서로 구분 가능해야 답글 멘션(@익명N)이 의미를 가진다.
     * 전체 row(삭제/숨김 포함)를 입력으로 받아 번호 앵커가 사라지지 않게 한다.
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

    /**
     * 화면에 보일 노드 id 집합.
     * PUBLISHED 노드는 모두 표시한다. 삭제/숨김 노드는 '살아있는(=표시되는) 자손'이 있으면
     * tombstone으로 골격만 유지한다. 디시식 2단계 평면화상 보통 루트지만,
     * 레거시 다단계 데이터도 부모 체인을 따라 일반화 처리한다.
     */
    private Set<Long> computeRenderable(List<CommunityComment> comments) {
        Map<Long, CommunityComment> byId = new HashMap<>(comments.size());
        for (CommunityComment c : comments) {
            byId.put(c.getId(), c);
        }
        Set<Long> renderable = new HashSet<>();
        for (CommunityComment c : comments) {
            if (!CommentStatus.PUBLISHED.name().equals(c.getStatus())) {
                continue;
            }
            renderable.add(c.getId());
            // 살아있는 노드의 조상 체인을 tombstone으로 표시(부모가 삭제/숨김이어도 트리 골격 유지).
            // add()가 false면 이미 처리된 조상이라 중단. guard로 순환 데이터 방어.
            Long pid = c.getParentId();
            int guard = 0;
            while (pid != null && byId.containsKey(pid) && renderable.add(pid) && guard++ < 1000) {
                pid = byId.get(pid).getParentId();
            }
        }
        return renderable;
    }

    /** 삭제/숨김 노드의 tombstone 응답. 본문·작성자·멘션은 비식별, 트리 골격(id/parentId/createdAt)만 유지. */
    private CommentResponse tombstone(CommunityComment c) {
        return new CommentResponse(
                c.getId(),
                c.getPostId(),
                c.getParentId(),
                null,
                new PostListResponse.AuthorDto(null, "", true),
                null,
                0,
                false,
                c.getCreatedAt(),
                false,
                true);
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

        // 게시글 검열과 동형: 커밋 후 비동기 검열(AFTER_COMMIT 리스너). 작성 즉시 PUBLISHED 로 노출되고,
        // toxic 판정 시에만 HIDDEN 으로 조건부 flip 된다(pending 윈도우엔 정상 표시).
        eventPublisher.publishEvent(new CommentModerationRequiredEvent(comment.getId()));

        log.info("댓글 작성 postId={} commentId={}", postId, comment.getId());
        // 작성 직후 응답은 프론트가 곧바로 목록을 재조회하므로 라벨은 단순값으로 둔다.
        return toResponse(comment, post.getUserId(), userId,
                comment.isAnonymous() ? "익명" : comment.getUserName(), null, false);
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
        // comment_count = PUBLISHED 댓글 수. PUBLISHED 경계를 통과할 때만 -1.
        // 이미 검열/관리자 숨김(HIDDEN)된 댓글을 자삭하면 0행 → 이중감소 없이 DELETED 로만 전환.
        int published = commentMapper.deleteCommentIfPublished(commentId);
        if (published > 0) {
            postMapper.decrementCommentCount(comment.getPostId());
        } else {
            commentMapper.updateStatus(commentId, CommentStatus.DELETED.name());
        }
    }

    private CommentResponse toResponse(CommunityComment c, Long postAuthorId, Long currentUserId,
                                       String authorName, String mentionLabel, boolean isDeleted) {
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
                liked,
                isDeleted);
    }
}
