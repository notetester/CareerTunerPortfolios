package com.careertuner.community.service;

import java.time.LocalDateTime;
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
import com.careertuner.community.domain.CommentReaction;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.CommunitySubscriptionMapper;
import com.careertuner.community.mapper.ReactionMapper;
import com.careertuner.community.moderation.event.CommentModerationRequiredEvent;
import com.careertuner.nickname.dto.DisplayNameQuery;
import com.careertuner.nickname.dto.DisplayNameResponse;
import com.careertuner.nickname.service.NicknameProfileService;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.privacy.service.PrivacyPolicyService;
import com.careertuner.privacy.service.PrivacySurfaces;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityCommentServiceImpl implements CommunityCommentService {

    /** 차단 작성자 댓글 톰스톤 문구 (docs/PERSONAL_BLOCK_POLICY.md §4 — silent deny). */
    private static final String BLOCKED_COMMENT_TOMBSTONE = "차단한 사용자의 댓글입니다.";

    private final CommunityCommentMapper commentMapper;
    private final CommunityPostMapper postMapper;
    private final ReactionMapper reactionMapper;
    private final CommunitySubscriptionMapper subscriptionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final PrivacyPolicyService privacyPolicyService;
    private final NicknameProfileService nicknameProfileService;
    /** 댓글 작성 rate-limit(도배 방지) 정책값 — 검열/중재 정책 콘솔에서 런타임 편집. */
    private final com.careertuner.community.moderation.service.ModerationSettingService moderationSettingService;

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
        // 비익명 작성자 표시명을 닉네임 프로필로 벌크 해석(N+1 방지). 익명 댓글은 익명번호 라벨을 유지한다.
        Map<DisplayNameQuery, DisplayNameResponse> resolvedNames = resolveCommentAuthorNames(comments);
        // 멘션 대상이 비익명이면 익명라벨이 없으므로 표시명(닉네임 프로필 반영)으로 폴백(작성자 표시 경로와 동일하게 비대칭 제거).
        // 멘션 대상은 반드시 이 글에 댓글을 단 사용자이므로 전체 목록에서 이름을 찾을 수 있다.
        // 같은 작성자라도 댓글별로 다른 프로필을 쓸 수 있으나, 멘션은 "사용자" 참조라 그 사용자의 마지막(=최신) 표시명을 쓴다.
        Map<Long, String> userNames = new HashMap<>();
        for (CommunityComment c : comments) {
            if (!c.isAnonymous()) {
                userNames.put(c.getUserId(), resolvedName(c, resolvedNames));
            }
        }

        // 표시 대상 = PUBLISHED 전체 + (살아있는 자손을 가진 삭제/숨김 노드)
        Set<Long> renderable = computeRenderable(comments);

        // 뷰어 기준 content.comment/reply(.anonymous) 차단 작성자 벌크 판정(비로그인 뷰어는 필터 없음)
        BlockedCommentAuthors blockedAuthors = resolveBlockedAuthors(comments, currentUserId);

        // 뷰어 리액션/구독 상태 벌크 조회(N+1 방지)
        Map<Long, Set<String>> viewerReactions = new HashMap<>();
        Set<Long> subscribedCommentIds = new HashSet<>();
        if (currentUserId != null) {
            for (CommentReaction r : reactionMapper.findCommentReactionsByUserForPost(currentUserId, postId)) {
                viewerReactions.computeIfAbsent(r.getCommentId(), k -> new HashSet<>()).add(r.getReactionType());
            }
            subscribedCommentIds.addAll(subscriptionMapper.findSubscribedCommentIds(currentUserId, postId));
        }

        List<CommentResponse> result = new ArrayList<>(comments.size());
        for (CommunityComment c : comments) {
            if (!renderable.contains(c.getId())) {
                continue; // 살아있는 자손 없는 삭제/숨김 leaf — 렌더 제외(단 위 번호 계산엔 이미 포함됨)
            }
            if (!CommentStatus.PUBLISHED.name().equals(c.getStatus())) {
                result.add(tombstone(c)); // 삭제/숨김이지만 자손이 살아있음 → 골격 유지용 tombstone
                continue;
            }
            if (blockedAuthors.contains(c)) {
                // 차단 작성자 댓글/답글 — 삭제 톰스톤과 동일 문법으로 비식별 처리(blocked=true)
                result.add(blockedTombstone(c));
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
            Set<String> reactions = viewerReactions.getOrDefault(c.getId(), Set.of());
            ViewerFlags flags = new ViewerFlags(
                    reactions.contains("LIKE"),
                    reactions.contains("DISLIKE"),
                    reactions.contains("RECOMMEND"),
                    reactions.contains("DISRECOMMEND"),
                    subscribedCommentIds.contains(c.getId()));
            result.add(toResponse(c, post.getUserId(), currentUserId,
                    displayName(c, anonLabels, resolvedNames), resolvedProfileId(c, resolvedNames),
                    mentionLabel, false, flags));
        }
        return result;
    }

    /** 뷰어의 댓글별 리액션/구독 상태 홀더. */
    private record ViewerFlags(boolean liked, boolean disliked, boolean recommended,
                               boolean disrecommended, boolean subscribed) {
        static final ViewerFlags EMPTY = new ViewerFlags(false, false, false, false, false);
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
                new PostListResponse.AuthorDto(null, "", null, true),
                null,
                0, 0, 0, 0,
                false,
                false,
                c.getCreatedAt(),
                false, false, false, false, false,
                true,
                false);
    }

    /**
     * 차단 작성자 댓글의 tombstone — 삭제 톰스톤과 동일 문법(작성자·멘션 비식별, 골격 유지)이되
     * 본문 자리에 안내 문구를 넣고 blocked=true 로 구분한다(isDeleted 와 별개).
     */
    private CommentResponse blockedTombstone(CommunityComment c) {
        return new CommentResponse(
                c.getId(),
                c.getPostId(),
                c.getParentId(),
                null,
                new PostListResponse.AuthorDto(null, "", null, true),
                BLOCKED_COMMENT_TOMBSTONE,
                0, 0, 0, 0,
                false,
                false,
                c.getCreatedAt(),
                false, false, false, false, false,
                false,
                true);
    }

    /**
     * 뷰어 기준 차단 작성자 집합 — 표면 키가 (루트/답글 × 익명 여부) 4가지로 갈리므로
     * 작성자 id 를 그룹별로 나눠 blockedAuthorsAmong 벌크 판정한다.
     */
    private BlockedCommentAuthors resolveBlockedAuthors(List<CommunityComment> comments, Long viewerId) {
        if (viewerId == null || comments.isEmpty()) {
            return BlockedCommentAuthors.EMPTY;
        }
        Map<String, Set<Long>> authorsBySurface = new HashMap<>();
        for (CommunityComment c : comments) {
            if (!CommentStatus.PUBLISHED.name().equals(c.getStatus())) {
                continue; // 삭제/숨김 노드는 이미 비식별 tombstone — 판정 불필요
            }
            authorsBySurface.computeIfAbsent(surfaceOf(c), k -> new HashSet<>()).add(c.getUserId());
        }
        Map<String, Set<Long>> blockedBySurface = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : authorsBySurface.entrySet()) {
            blockedBySurface.put(entry.getKey(),
                    privacyPolicyService.blockedAuthorsAmong(viewerId, entry.getValue(), entry.getKey()));
        }
        return new BlockedCommentAuthors(blockedBySurface);
    }

    /** 댓글의 콘텐츠 표면 키 — 답글(parentId!=null)은 content.reply, 루트는 content.comment (+.anonymous). */
    private static String surfaceOf(CommunityComment c) {
        String base = c.getParentId() != null ? PrivacySurfaces.CONTENT_REPLY : PrivacySurfaces.CONTENT_COMMENT;
        return c.isAnonymous() ? base + ".anonymous" : base;
    }

    /** 표면 키별 차단 작성자 집합 홀더. */
    private record BlockedCommentAuthors(Map<String, Set<Long>> blockedBySurface) {
        static final BlockedCommentAuthors EMPTY = new BlockedCommentAuthors(Map.of());

        boolean contains(CommunityComment c) {
            Set<Long> blocked = blockedBySurface.get(surfaceOf(c));
            return blocked != null && blocked.contains(c.getUserId());
        }
    }

    private String displayName(CommunityComment c, Map<Long, String> anonLabels,
                               Map<DisplayNameQuery, DisplayNameResponse> resolvedNames) {
        return c.isAnonymous()
                ? anonLabels.getOrDefault(c.getUserId(), "익명")
                : resolvedName(c, resolvedNames);
    }

    /** 목록의 비익명 작성자 (userId, nicknameProfileId) 를 모아 표시명을 한 번에 해석한다. */
    private Map<DisplayNameQuery, DisplayNameResponse> resolveCommentAuthorNames(List<CommunityComment> comments) {
        Set<DisplayNameQuery> queries = new HashSet<>();
        for (CommunityComment c : comments) {
            if (!c.isAnonymous()) {
                queries.add(new DisplayNameQuery(c.getUserId(), c.getNicknameProfileId()));
            }
        }
        return nicknameProfileService.bulkResolveDisplayNames(queries);
    }

    /** 벌크 해석 결과에서 이 댓글의 비익명 표시명을 꺼낸다(미해석 시 users.name 폴백). */
    private String resolvedName(CommunityComment c, Map<DisplayNameQuery, DisplayNameResponse> resolvedNames) {
        DisplayNameResponse dn = resolvedNames.get(new DisplayNameQuery(c.getUserId(), c.getNicknameProfileId()));
        return dn != null ? dn.displayName() : c.getUserName();
    }

    /** 비익명 댓글의 표시용 프로필 id(해석 성공 시) — AuthorDto 에 실어 보낸다. */
    private Long resolvedProfileId(CommunityComment c, Map<DisplayNameQuery, DisplayNameResponse> resolvedNames) {
        DisplayNameResponse dn = resolvedNames.get(new DisplayNameQuery(c.getUserId(), c.getNicknameProfileId()));
        return dn != null ? dn.nicknameProfileId() : c.getNicknameProfileId();
    }

    @Override
    @Transactional
    public CommentResponse createComment(Long postId, CreateCommentRequest request, Long userId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }

        // 작성 rate-limit(도배 방지) — 최근 window 초 안에 max 건 이상이면 429. (콘솔 편집값)
        int commentRateMax = moderationSettingService.getCommentRateMax();
        if (commentRateMax > 0) {
            int recent = commentMapper.countRecentCommentsByUser(userId,
                    LocalDateTime.now().minusSeconds(moderationSettingService.getCommentRateWindowSeconds()));
            if (recent >= commentRateMax) {
                throw new BusinessException(ErrorCode.RATE_LIMITED,
                        "짧은 시간에 너무 많은 댓글을 작성했습니다. 잠시 후 다시 시도해 주세요.");
            }
        }

        // 디시인사이드식 2단계: 답글의 답글도 깊어지지 않고 최상위 댓글 그룹에 붙인다.
        //  - parentId        = 최상위 댓글(루트 그룹). 삭제에 강건.
        //  - mentionUserId   = 답글이 실제로 가리키는 대상 사용자(불변 참조). 표시명은 읽을 때 동적 렌더.
        // 클라이언트는 클릭한 댓글 id만 보내고, 정규화·멘션 산출은 서버가 한다.
        Long effectiveParentId = null;
        Long mentionUserId = null;
        Long replyTargetUserId = null; // 답글이 실제로 가리키는 사용자(COMMENT_REPLY 알림 수신자 후보)
        if (request.parentId() != null) {
            CommunityComment target = commentMapper.findById(request.parentId());
            if (target == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "부모 댓글을 찾을 수 없습니다.");
            }
            if (!target.getPostId().equals(postId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "다른 게시글의 댓글에는 답글을 달 수 없습니다.");
            }
            // 삭제(DELETED)/숨김(HIDDEN) 부모에 답글을 허용하면 tombstone 골격이 다시 노출돼 죽은 댓글이 "부활"한다.
            // 수정 경로(updateContentIfPublished)와 동일하게 PUBLISHED 부모에만 답글을 허용한다.
            if (!CommentStatus.PUBLISHED.name().equals(target.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "삭제되었거나 숨겨진 댓글에는 답글을 달 수 없습니다.");
            }
            effectiveParentId = target.getParentId() != null ? target.getParentId() : target.getId();
            // 답글 알림 대상 = 멘션 대상(대댓글 답글). 멘션이 없으면 클릭한 부모 댓글 작성자.
            replyTargetUserId = target.getUserId();
            // 대댓글에 단 답글이면 대상 사용자를 멘션으로 기록.
            // 단 루트 직속 답글이거나 자기 자신에게 다는 답글이면 멘션 불필요.
            if (target.getParentId() != null && !userId.equals(target.getUserId())) {
                mentionUserId = target.getUserId();
            }
        }

        boolean anonymous = request.anonymous() == null || request.anonymous();
        CommunityComment comment = CommunityComment.builder()
                .postId(postId)
                .userId(userId)
                .parentId(effectiveParentId)
                .mentionUserId(mentionUserId)
                .content(request.content())
                .anonymous(anonymous)
                .nicknameProfileId(normalizeProfileId(userId, anonymous, request.nicknameProfileId()))
                .status(CommentStatus.PUBLISHED.name())
                .build();
        commentMapper.insert(comment);
        postMapper.incrementCommentCount(postId);

        // 게시글 검열과 동형: 커밋 후 비동기 검열(AFTER_COMMIT 리스너). 작성 즉시 PUBLISHED 로 노출되고,
        // toxic 판정 시에만 HIDDEN 으로 조건부 flip 된다(pending 윈도우엔 정상 표시).
        eventPublisher.publishEvent(new CommentModerationRequiredEvent(comment.getId()));

        // 댓글/답글 알림 + 구독자 팬아웃 — 발행 실패가 댓글 작성을 깨지 않도록 best-effort.
        try {
            notifyCommentCreated(post, comment, replyTargetUserId, request.parentId());
        } catch (Exception e) {
            log.error("댓글 알림 발행 실패: commentId={}", comment.getId(), e);
        }

        log.info("댓글 작성 postId={} commentId={}", postId, comment.getId());
        // 작성 직후 응답 표시명 — 비익명은 선택 프로필로 해석(익명은 "익명"). 익명번호 라벨은 목록 재조회에서 부여된다.
        DisplayNameResponse dn = comment.isAnonymous() ? null
                : nicknameProfileService.resolveDisplayName(userId, comment.getNicknameProfileId());
        String authorName = comment.isAnonymous() ? "익명"
                : (dn != null ? dn.displayName() : comment.getUserName());
        Long authorProfileId = dn != null ? dn.nicknameProfileId() : comment.getNicknameProfileId();
        return toResponse(comment, post.getUserId(), userId,
                authorName, authorProfileId, null, false, ViewerFlags.EMPTY);
    }

    @Override
    @Transactional
    public CommentResponse updateComment(Long commentId, String content, Long userId) {
        CommunityComment comment = commentMapper.findById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 댓글만 수정할 수 있습니다.");
        }
        // PUBLISHED 댓글의 본문만 수정. 숨김/삭제 댓글은 0행 → 수정 불가.
        int updated = commentMapper.updateContentIfPublished(commentId, content);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "숨김 또는 삭제된 댓글은 수정할 수 없습니다.");
        }
        // 편집 본문 재검열(작성·게시글 편집과 동형 — toxic 시 HIDDEN 으로 조건부 flip).
        eventPublisher.publishEvent(new CommentModerationRequiredEvent(commentId));
        log.info("댓글 수정 commentId={}", commentId);
        CommunityComment fresh = commentMapper.findById(commentId);
        CommunityPost post = postMapper.findById(fresh.getPostId());
        Long postAuthorId = post != null ? post.getUserId() : null;
        // 응답 라벨은 단순값(익명 등). FE 는 낙관적으로 본문만 갱신한다(편집 흐름은 목록 재조회 안 함 —
        // 작성의 pending 윈도우와 동형: 편집 본문이 재검열로 HIDDEN 되면 다음 재조회/타 클라에서 반영).
        DisplayNameResponse dn = fresh.isAnonymous() ? null
                : nicknameProfileService.resolveDisplayName(userId, fresh.getNicknameProfileId());
        String authorName = fresh.isAnonymous() ? "익명"
                : (dn != null ? dn.displayName() : fresh.getUserName());
        Long authorProfileId = dn != null ? dn.nicknameProfileId() : fresh.getNicknameProfileId();
        return toResponse(fresh, postAuthorId, userId,
                authorName, authorProfileId, null, false, ViewerFlags.EMPTY);
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

    /**
     * 댓글 작성 알림 발행 + 구독자 팬아웃.
     *  - 루트 댓글: 게시글 작성자에게 COMMENT.
     *  - 답글(parentId 있음): 실제 답글 대상(멘션 대상, 없으면 클릭한 부모 댓글 작성자)에게 COMMENT_REPLY.
     *    답글 대상과 게시글 작성자가 다르면 게시글 작성자에게도 COMMENT 발행(중복 수신자면 한 번만).
     *  - 글 구독자: POST_WATCH_COMMENT. 답글이면 클릭 대상·루트 댓글 구독자: COMMENT_WATCH_REPLY.
     *    작성자 본인 제외, 기존 COMMENT/COMMENT_REPLY 수신자와 중복이면 1회만(구독 알림 스킵).
     *  - 본인에게는 발행하지 않는다. senderRelation·개인 차단·수신 설정 필터는 notify()가 처리한다.
     * 링크/타깃은 검열 알림(PostModerationService)과 동일 패턴: /community/posts/{postId}, COMMENT/commentId.
     */
    private void notifyCommentCreated(CommunityPost post, CommunityComment comment,
                                      Long replyTargetUserId, Long clickedCommentId) {
        Long actorId = comment.getUserId();
        String preview = truncate(comment.getContent(), 80);
        String link = "/community/posts/" + post.getId();

        // 이미 알림을 받은(또는 받을) 수신자 — 구독 팬아웃에서 제외해 1인 1회 계약을 지킨다
        Set<Long> notified = new HashSet<>();
        notified.add(actorId);

        Long replyRecipientId = null;
        if (comment.getParentId() != null && replyTargetUserId != null && !replyTargetUserId.equals(actorId)) {
            replyRecipientId = replyTargetUserId;
            notified.add(replyRecipientId);
            notificationService.notify(Notification.builder()
                    .userId(replyRecipientId)
                    .actorId(actorId)
                    .type("COMMENT_REPLY")
                    .targetType("COMMENT")
                    .targetId(comment.getId())
                    .title("내 댓글에 답글이 달렸습니다.")
                    .message(preview)
                    .link(link)
                    .build());
        }

        // 게시글 작성자 COMMENT 알림 — 본인 댓글이거나 이미 답글 알림을 받은 수신자면 스킵.
        Long postAuthorId = post.getUserId();
        if (!postAuthorId.equals(actorId) && !postAuthorId.equals(replyRecipientId)) {
            notified.add(postAuthorId);
            notificationService.notify(Notification.builder()
                    .userId(postAuthorId)
                    .actorId(actorId)
                    .type("COMMENT")
                    .targetType("COMMENT")
                    .targetId(comment.getId())
                    .title("새 댓글이 달렸습니다.")
                    .message(preview)
                    .link(link)
                    .build());
        }

        // 댓글 구독자 팬아웃 — 답글이면 클릭 대상 댓글 + 루트 댓글 구독자(중복 제거)
        if (comment.getParentId() != null) {
            List<Long> watchTargets = new ArrayList<>();
            if (clickedCommentId != null) watchTargets.add(clickedCommentId);
            if (!comment.getParentId().equals(clickedCommentId)) watchTargets.add(comment.getParentId());
            if (!watchTargets.isEmpty()) {
                for (Long subscriberId : subscriptionMapper.findCommentSubscriberIds(watchTargets)) {
                    if (!notified.add(subscriberId)) continue;
                    notificationService.notify(Notification.builder()
                            .userId(subscriberId)
                            .actorId(actorId)
                            .type("COMMENT_WATCH_REPLY")
                            .targetType("COMMENT")
                            .targetId(comment.getId())
                            .title("구독한 댓글에 새 답글이 달렸습니다.")
                            .message(preview)
                            .link(link)
                            .build());
                }
            }
        }

        // 글 구독자 팬아웃 — 작성자 본인·이미 알림받은 수신자 제외
        for (Long subscriberId : subscriptionMapper.findPostSubscriberIds(post.getId())) {
            if (!notified.add(subscriberId)) continue;
            notificationService.notify(Notification.builder()
                    .userId(subscriberId)
                    .actorId(actorId)
                    .type("POST_WATCH_COMMENT")
                    .targetType("COMMENT")
                    .targetId(comment.getId())
                    .title("구독한 게시글에 새 댓글이 달렸습니다.")
                    .message(preview)
                    .link(link)
                    .build());
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    /**
     * 저장할 닉네임 프로필 id 를 정규화한다(게시글과 동일 규칙).
     * 익명이거나 profileId 가 없으면 null. 지정 프로필이 작성자 소유 ACTIVE 가 아니면 null(위조 방지).
     */
    private Long normalizeProfileId(Long userId, boolean anonymous, Long profileId) {
        if (anonymous || profileId == null) {
            return null;
        }
        DisplayNameResponse dn = nicknameProfileService.resolveDisplayName(userId, profileId);
        return dn != null && profileId.equals(dn.nicknameProfileId()) ? profileId : null;
    }

    private CommentResponse toResponse(CommunityComment c, Long postAuthorId, Long currentUserId,
                                       String authorName, Long nicknameProfileId, String mentionLabel,
                                       boolean isDeleted, ViewerFlags flags) {
        return new CommentResponse(
                c.getId(),
                c.getPostId(),
                c.getParentId(),
                mentionLabel,
                new PostListResponse.AuthorDto(
                        c.isAnonymous() ? null : c.getUserId(),
                        authorName,
                        c.isAnonymous() ? null : nicknameProfileId,
                        c.isAnonymous()),
                c.getContent(),
                c.getLikeCount(),
                c.getDislikeCount(),
                c.getRecommendCount(),
                c.getDisrecommendCount(),
                c.getUserId().equals(postAuthorId),
                currentUserId != null && currentUserId.equals(c.getUserId()),
                c.getCreatedAt(),
                flags.liked(),
                flags.disliked(),
                flags.recommended(),
                flags.disrecommended(),
                flags.subscribed(),
                isDeleted,
                false);
    }
}
