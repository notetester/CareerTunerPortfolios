package com.careertuner.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.community.domain.CommentStatus;
import com.careertuner.community.domain.CommunityComment;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.dto.CommentResponse;
import com.careertuner.community.dto.CreateCommentRequest;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.ReactionMapper;

/**
 * 멘션/삭제 감사 수정(M1·M2·L2·L4) 회귀 테스트.
 * 각 테스트가 한 버그를 증명한다. 익명번호의 SQL tie-break(L4)는 매퍼 ORDER BY로 보장되므로,
 * 여기서는 "매퍼가 준 순서대로 익명번호가 결정적으로 부여되는지"를 검증한다.
 */
class CommunityCommentServiceImplTest {

    private final CommunityCommentMapper commentMapper = mock(CommunityCommentMapper.class);
    private final CommunityPostMapper postMapper = mock(CommunityPostMapper.class);
    private final ReactionMapper reactionMapper = mock(ReactionMapper.class);

    private final CommunityCommentServiceImpl service =
            new CommunityCommentServiceImpl(commentMapper, postMapper, reactionMapper);

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
}
