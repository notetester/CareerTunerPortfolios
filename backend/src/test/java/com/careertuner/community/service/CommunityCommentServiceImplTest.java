package com.careertuner.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.CommentStatus;
import com.careertuner.community.domain.CommunityComment;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.dto.CommentResponse;
import com.careertuner.community.dto.CreateCommentRequest;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.ReactionMapper;
import com.careertuner.community.moderation.event.CommentModerationRequiredEvent;
import com.careertuner.notification.service.NotificationService;

/**
 * 멘션/삭제 감사 수정(M1·M2·L2·L4) 회귀 테스트.
 * 각 테스트가 한 버그를 증명한다. 익명번호의 SQL tie-break(L4)는 매퍼 ORDER BY로 보장되므로,
 * 여기서는 "매퍼가 준 순서대로 익명번호가 결정적으로 부여되는지"를 검증한다.
 */
class CommunityCommentServiceImplTest {

    private final CommunityCommentMapper commentMapper = mock(CommunityCommentMapper.class);
    private final CommunityPostMapper postMapper = mock(CommunityPostMapper.class);
    private final ReactionMapper reactionMapper = mock(ReactionMapper.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    private final CommunityCommentServiceImpl service =
            new CommunityCommentServiceImpl(commentMapper, postMapper, reactionMapper, eventPublisher,
                    notificationService);

    private static final long POST_ID = 1L;
    private static final long POST_AUTHOR = 99L;
    private final LocalDateTime t0 = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    private CommunityComment cmt(long id, Long parentId, Long mentionUserId, long userId,
                                 boolean anon, String status, String userName, int plusSec) {
        return CommunityComment.builder()
                .id(id).postId(POST_ID).parentId(parentId).mentionUserId(mentionUserId)
                .userId(userId).anonymous(anon).status(status)
                .content("c" + id).userName(userName)
                .createdAt(t0.plusSeconds(plusSec))
                .build();
    }

    private void givenPost() {
        when(postMapper.findById(POST_ID)).thenReturn(
                CommunityPost.builder().id(POST_ID).userId(POST_AUTHOR)
                        .status(PostStatus.PUBLISHED.name()).build());
    }

    private CommentResponse byId(List<CommentResponse> rs, long id) {
        return rs.stream().filter(r -> r.id() == id).findFirst().orElseThrow();
    }

    // ── M1: 삭제된 루트 + 살아있는 자식 → 자식이 tombstone 아래로 그대로 보임 ──
    @Test
    void deletedRoot_keepsAliveChildAsVisible_underTombstone() {
        givenPost();
        when(commentMapper.findAllByPostId(POST_ID)).thenReturn(List.of(
                cmt(1, null, null, 10, true, CommentStatus.DELETED.name(), "A", 0),
                cmt(2, 1L, null, 11, true, CommentStatus.PUBLISHED.name(), "B", 1)
        ));

        List<CommentResponse> rs = service.getComments(POST_ID, null);

        assertThat(rs).hasSize(2);
        CommentResponse root = byId(rs, 1);
        assertThat(root.isDeleted()).isTrue();          // 루트는 tombstone
        assertThat(root.content()).isNull();            // 본문 비식별
        assertThat(byId(rs, 2).isDeleted()).isFalse();  // 살아있는 자식은 그대로 노출
        assertThat(byId(rs, 2).parentId()).isEqualTo(1L);
    }

    // ── M1: 관리자 HIDDEN 루트도 동일하게 tombstone 처리(서버 플래그 기반) ──
    @Test
    void hiddenRoot_alsoTombstoned() {
        givenPost();
        when(commentMapper.findAllByPostId(POST_ID)).thenReturn(List.of(
                cmt(1, null, null, 10, true, CommentStatus.HIDDEN.name(), "A", 0),
                cmt(2, 1L, null, 11, true, CommentStatus.PUBLISHED.name(), "B", 1)
        ));

        List<CommentResponse> rs = service.getComments(POST_ID, null);

        assertThat(byId(rs, 1).isDeleted()).isTrue();
        assertThat(byId(rs, 2).isDeleted()).isFalse();
    }

    // ── M1: 살아있는 자손이 없는 삭제 leaf는 렌더에서 제외 ──
    @Test
    void deletedLeaf_withNoAliveDescendant_isExcluded() {
        givenPost();
        when(commentMapper.findAllByPostId(POST_ID)).thenReturn(List.of(
                cmt(1, null, null, 10, true, CommentStatus.PUBLISHED.name(), "A", 0),
                cmt(2, 1L, null, 11, true, CommentStatus.DELETED.name(), "B", 1)  // 자손 없는 삭제 답글
        ));

        List<CommentResponse> rs = service.getComments(POST_ID, null);

        assertThat(rs).extracting(CommentResponse::id).containsExactly(1L); // 2는 제외
    }

    // ── M2: 앞 익명 사용자가 자삭해도 뒷 사용자 번호가 밀리지 않음(멘션 라벨 불변) ──
    @Test
    void deletedAnonAnchor_keepsLaterAnonNumberStable() {
        givenPost();
        when(commentMapper.findAllByPostId(POST_ID)).thenReturn(List.of(
                cmt(1, null, null, 10, true, CommentStatus.DELETED.name(), "A", 0),   // 익명1 앵커(삭제됐어도 유지)
                cmt(2, 1L, null, 20, true, CommentStatus.PUBLISHED.name(), "B", 1),   // 익명2
                cmt(3, 1L, 20L, 30, true, CommentStatus.PUBLISHED.name(), "C", 2)     // user20(익명2)을 멘션
        ));

        List<CommentResponse> rs = service.getComments(POST_ID, null);

        // 앵커가 사라졌다면 user20이 익명1로 당겨졌을 것. 앵커 보존으로 익명2 유지.
        assertThat(byId(rs, 2).author().name()).isEqualTo("익명2");
        assertThat(byId(rs, 3).mentionLabel()).isEqualTo("익명2");
    }

    // ── L2(버그 B): 멘션 대상이 비익명이면 실명으로 폴백 ──
    @Test
    void mentionTarget_whenNonAnonymous_fallsBackToRealName() {
        givenPost();
        when(commentMapper.findAllByPostId(POST_ID)).thenReturn(List.of(
                cmt(1, null, null, 10, true, CommentStatus.PUBLISHED.name(), "A", 0),
                cmt(2, 1L, null, 20, false, CommentStatus.PUBLISHED.name(), "김철수", 1), // 비익명
                cmt(3, 1L, 20L, 30, true, CommentStatus.PUBLISHED.name(), "C", 2)        // user20 멘션
        ));

        List<CommentResponse> rs = service.getComments(POST_ID, null);

        // 익명라벨이 없는 대상 → 과거엔 null(멘션 소실). 이제 실명으로 표시.
        assertThat(byId(rs, 3).mentionLabel()).isEqualTo("김철수");
    }

    // ── L4: 매퍼가 준 순서대로 익명번호가 결정적으로 부여(동일초 tie는 SQL id 정렬로 고정) ──
    @Test
    void anonNumbers_followDeterministicOrder() {
        givenPost();
        when(commentMapper.findAllByPostId(POST_ID)).thenReturn(List.of(
                cmt(5, null, null, 50, true, CommentStatus.PUBLISHED.name(), "X", 0),
                cmt(6, null, null, 60, true, CommentStatus.PUBLISHED.name(), "Y", 0) // 같은 시각이라도 id 순서로 고정
        ));

        List<CommentResponse> rs = service.getComments(POST_ID, null);

        assertThat(byId(rs, 5).author().name()).isEqualTo("익명1");
        assertThat(byId(rs, 6).author().name()).isEqualTo("익명2");
    }

    // ── 불변식 A + 자기멘션 제외(:99) 보존: 작성 경로가 멘션을 올바르게 부여 ──
    @Test
    void createComment_preservesMentionRules() {
        givenPost();
        ArgumentCaptor<CommunityComment> cap = ArgumentCaptor.forClass(CommunityComment.class);

        // (A) 루트에 답글 → 멘션 없음
        when(commentMapper.findById(5L)).thenReturn(
                cmt(5, null, null, 10, true, CommentStatus.PUBLISHED.name(), "A", 0));
        service.createComment(POST_ID, new CreateCommentRequest("x", 5L, true), 20L);

        // (B) 다른 사람의 대댓글에 답글 → 대상 멘션, parentId는 루트로 평면화
        when(commentMapper.findById(6L)).thenReturn(
                cmt(6, 5L, null, 10, true, CommentStatus.PUBLISHED.name(), "A", 1));
        service.createComment(POST_ID, new CreateCommentRequest("y", 6L, true), 20L);

        // (C) 자기 자신의 대댓글에 답글 → 멘션 없음(자기멘션 제외)
        when(commentMapper.findById(7L)).thenReturn(
                cmt(7, 5L, null, 20, true, CommentStatus.PUBLISHED.name(), "B", 2));
        service.createComment(POST_ID, new CreateCommentRequest("z", 7L, true), 20L);

        verify(commentMapper, org.mockito.Mockito.times(3)).insert(cap.capture());
        List<CommunityComment> inserted = cap.getAllValues();
        // (A)
        assertThat(inserted.get(0).getParentId()).isEqualTo(5L);
        assertThat(inserted.get(0).getMentionUserId()).isNull();
        // (B)
        assertThat(inserted.get(1).getParentId()).isEqualTo(5L);
        assertThat(inserted.get(1).getMentionUserId()).isEqualTo(10L);
        // (C)
        assertThat(inserted.get(2).getParentId()).isEqualTo(5L);
        assertThat(inserted.get(2).getMentionUserId()).isNull();
    }

    // ══════════════ #4 삭제댓글 답글 부활 차단 (답글 생성 시 부모 status 가드) ══════════════

    // ── PUBLISHED 부모에 답글 → 정상 생성 ──
    @Test
    void reply_toPublishedParent_succeeds() {
        givenPost();
        when(commentMapper.findById(5L)).thenReturn(
                cmt(5, null, null, 10, true, CommentStatus.PUBLISHED.name(), "A", 0));

        service.createComment(POST_ID, new CreateCommentRequest("x", 5L, true), 20L);

        verify(commentMapper).insert(any(CommunityComment.class));
        verify(postMapper).incrementCommentCount(POST_ID);
    }

    // ── DELETED 부모에 답글 → 거부(부활 차단), 삽입/카운트 없음 ──
    @Test
    void reply_toDeletedParent_isRejected() {
        givenPost();
        when(commentMapper.findById(5L)).thenReturn(
                cmt(5, null, null, 10, true, CommentStatus.DELETED.name(), "A", 0));

        assertThatThrownBy(() ->
                service.createComment(POST_ID, new CreateCommentRequest("x", 5L, true), 20L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        verify(commentMapper, never()).insert(any());
        verify(postMapper, never()).incrementCommentCount(anyLong());
    }

    // ── HIDDEN(검열 숨김) 부모에 답글 → 거부, 삽입/카운트 없음 ──
    @Test
    void reply_toHiddenParent_isRejected() {
        givenPost();
        when(commentMapper.findById(5L)).thenReturn(
                cmt(5, null, null, 10, true, CommentStatus.HIDDEN.name(), "A", 0));

        assertThatThrownBy(() ->
                service.createComment(POST_ID, new CreateCommentRequest("x", 5L, true), 20L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        verify(commentMapper, never()).insert(any());
        verify(postMapper, never()).incrementCommentCount(anyLong());
    }

    // ── 최상위 댓글(부모 없음) 생성은 가드 영향 없음 ──
    @Test
    void topLevelComment_isUnaffectedByParentGuard() {
        givenPost();

        service.createComment(POST_ID, new CreateCommentRequest("hello", null, true), 10L);

        verify(commentMapper).insert(any(CommunityComment.class));
        verify(postMapper).incrementCommentCount(POST_ID);
        verify(commentMapper, never()).findById(anyLong()); // 부모 조회 자체가 없음
    }

    // ══════════════ 댓글 검열 도입 회귀 테스트 (시나리오 1·2·4·5·6·7) ══════════════

    // ── 시나리오 1: 작성 직후 검열 트리거(이벤트) 발행 + pending 윈도우엔 PUBLISHED 로 정상 표시 ──
    @Test
    void createComment_publishesModerationEvent_andStaysPublishedDuringPendingWindow() {
        givenPost();
        ArgumentCaptor<CommentModerationRequiredEvent> ev =
                ArgumentCaptor.forClass(CommentModerationRequiredEvent.class);

        service.createComment(POST_ID, new CreateCommentRequest("hello", null, true), 10L);

        // 검열은 AFTER_COMMIT 비동기 리스너가 받는다 — 작성 트랜잭션에서 이벤트만 발행.
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue()).isInstanceOf(CommentModerationRequiredEvent.class);

        // pending 윈도우: 아직 검열 전이라 status=PUBLISHED → getComments 에 그대로 노출(작성자 본인 포함).
        when(commentMapper.findAllByPostId(POST_ID)).thenReturn(List.of(
                cmt(1, null, null, 10, true, CommentStatus.PUBLISHED.name(), "A", 0)));
        List<CommentResponse> rs = service.getComments(POST_ID, 10L);
        assertThat(rs).hasSize(1);
        assertThat(byId(rs, 1).isDeleted()).isFalse();
    }

    // ── 시나리오 5(경계): 검열 HIDDEN 댓글을 자삭해도 comment_count 이중감소 없음(조건부 전이) ──
    @Test
    void deleteComment_onAlreadyHidden_doesNotDoubleDecrement() {
        when(commentMapper.findById(2L)).thenReturn(
                cmt(2, null, null, 10, true, CommentStatus.HIDDEN.name(), "A", 0));
        when(commentMapper.deleteCommentIfPublished(2L)).thenReturn(0); // 이미 HIDDEN → 0행

        service.deleteComment(2L, 10L);

        verify(postMapper, never()).decrementCommentCount(anyLong()); // 이미 숨김 시 빠졌으므로 재감소 없음
        verify(commentMapper).updateStatus(2L, CommentStatus.DELETED.name()); // 상태만 DELETED 로 전환
    }

    // ── 시나리오 5(정상): PUBLISHED 자삭은 한 번만 감소 ──
    @Test
    void deleteComment_onPublished_decrementsOnce() {
        when(commentMapper.findById(1L)).thenReturn(
                cmt(1, null, null, 10, true, CommentStatus.PUBLISHED.name(), "A", 0));
        when(commentMapper.deleteCommentIfPublished(1L)).thenReturn(1); // PUBLISHED→DELETED 경계 통과

        service.deleteComment(1L, 10L);

        verify(postMapper).decrementCommentCount(POST_ID);
        verify(commentMapper, never()).updateStatus(eq(1L), any()); // 조건부 전이가 처리 → 강제 updateStatus 호출 안 함
    }

    // ── 시나리오 6: 검열 HIDDEN 된 사용자 대상 @멘션 → 여전히 올바른 익명 라벨로 표시 ──
    @Test
    void mentionTarget_whenHiddenByModeration_keepsCorrectAnonLabel() {
        givenPost();
        when(commentMapper.findAllByPostId(POST_ID)).thenReturn(List.of(
                cmt(1, null, null, 10, true, CommentStatus.PUBLISHED.name(), "A", 0),   // 익명1
                cmt(2, 1L, null, 20, true, CommentStatus.HIDDEN.name(), "B", 1),        // 익명2 — 검열로 숨김
                cmt(3, 1L, 20L, 30, true, CommentStatus.PUBLISHED.name(), "C", 2)       // user20(익명2) 멘션
        ));

        List<CommentResponse> rs = service.getComments(POST_ID, null);

        // 숨김 노드도 익명 앵커(findAllByPostId)로 살아있어 멘션이 올바른 사람을 가리킨다.
        assertThat(byId(rs, 3).mentionLabel()).isEqualTo("익명2");
    }

    // ── 시나리오 7: 검열 HIDDEN 후에도 뒷 사용자 익명 번호 불변(앵커 보존) ──
    @Test
    void hiddenByModeration_keepsLaterAnonNumberStable() {
        givenPost();
        when(commentMapper.findAllByPostId(POST_ID)).thenReturn(List.of(
                cmt(1, null, null, 10, true, CommentStatus.HIDDEN.name(), "A", 0),   // 익명1 — 검열 숨김(앵커 유지)
                cmt(2, 1L, null, 20, true, CommentStatus.PUBLISHED.name(), "B", 1)   // 익명2 — 밀리면 안 됨
        ));

        List<CommentResponse> rs = service.getComments(POST_ID, null);

        assertThat(byId(rs, 2).author().name()).isEqualTo("익명2");
    }
}
