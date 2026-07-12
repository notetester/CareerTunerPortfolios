package com.careertuner.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.dto.PostDetailResponse;
import com.careertuner.community.dto.PostPageResponse;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.CommunitySubscriptionMapper;
import com.careertuner.community.mapper.CommunityTagMapper;
import com.careertuner.community.mapper.PostScrapMapper;
import com.careertuner.community.mapper.ReactionMapper;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.nickname.service.NicknameProfileService;
import com.careertuner.privacy.service.PrivacyPolicyService;
import com.careertuner.reward.service.RewardService;

import tools.jackson.databind.ObjectMapper;

/**
 * 커뮤니티 감사 수정 회귀 테스트 (2026-07-11).
 * - #2 익명 글 수정/삭제: 상세 응답의 mine 플래그로 소유자를 판정한다(익명이라 author.id 는 null 유지).
 *   과거엔 프론트가 author.id 로만 소유자를 가려 익명 글의 수정/삭제 버튼이 항상 숨겨졌다.
 * - #1 전체 글 로딩: 목록이 서버 페이지네이션(offset=page*size)과 total(전체 건수)을 정확히 내려
 *   프론트 Pager 가 100건 상한 없이 전체 페이지를 계산할 수 있다.
 */
class CommunityPostServiceImplMineAndPagingTest {

    private final CommunityPostMapper postMapper = mock(CommunityPostMapper.class);
    private final CommunityTagMapper tagMapper = mock(CommunityTagMapper.class);
    private final ReactionMapper reactionMapper = mock(ReactionMapper.class);
    private final PostScrapMapper scrapMapper = mock(PostScrapMapper.class);
    private final CommunitySubscriptionMapper subscriptionMapper = mock(CommunitySubscriptionMapper.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PostAiResultMapper aiResultMapper = mock(PostAiResultMapper.class);
    private final PrivacyPolicyService privacyPolicyService = mock(PrivacyPolicyService.class);
    private final NicknameProfileService nicknameProfileService = mock(NicknameProfileService.class);
    private final PersonalizedFeedService personalizedFeedService = mock(PersonalizedFeedService.class);
    private final com.careertuner.community.moderation.service.ModerationSettingService moderationSettingService =
            mock(com.careertuner.community.moderation.service.ModerationSettingService.class);
    private final RewardService rewardService = mock(RewardService.class);

    private final CommunityPostServiceImpl service = new CommunityPostServiceImpl(
            postMapper, tagMapper, reactionMapper, scrapMapper, subscriptionMapper, objectMapper,
            eventPublisher, aiResultMapper, privacyPolicyService, nicknameProfileService,
            personalizedFeedService, moderationSettingService, rewardService);

    private static final long POST_ID = 1L;
    private static final long AUTHOR = 99L;
    private static final long OTHER = 77L;

    private CommunityPost anonymousPost() {
        return CommunityPost.builder()
                .id(POST_ID).userId(AUTHOR).category("FREE")
                .title("제목").content("본문")
                .status(PostStatus.PUBLISHED.name()).anonymous(true)
                .build();
    }

    private void stubDetailCommon() {
        when(postMapper.findById(POST_ID)).thenReturn(anonymousPost());
        // 차단 없음(allows=true) — 톰스톤 경로로 빠지지 않게.
        when(privacyPolicyService.allows(anyLong(), anyLong(), anyString())).thenReturn(true);
        when(reactionMapper.findPostReactionsByUser(anyLong(), anyLong())).thenReturn(List.of());
        // 블러 이미지 없음.
        when(aiResultMapper.findByPostIdAndTaskType(any(), any())).thenReturn(null);
    }

    // ── #2: 익명 글을 본인이 열면 mine=true, 그래도 author.id 는 마스킹(null) 유지 ──
    @Test
    void anonymousPost_ownerViewer_getsMineTrue_butAuthorIdStillMasked() {
        stubDetailCommon();

        PostDetailResponse resp = service.getPostDetail(POST_ID, AUTHOR);

        assertThat(resp.mine()).isTrue();                 // 소유자 판정 → 프론트 수정/삭제 버튼 노출
        assertThat(resp.author().id()).isNull();          // 익명성 유지(닉네임/실명 노출 안 함)
        assertThat(resp.author().isAnonymous()).isTrue();
        assertThat(resp.author().name()).isEqualTo("익명");
    }

    // ── #2: 같은 익명 글을 남이 열면 mine=false ──
    @Test
    void anonymousPost_otherViewer_getsMineFalse() {
        stubDetailCommon();

        PostDetailResponse resp = service.getPostDetail(POST_ID, OTHER);

        assertThat(resp.mine()).isFalse();
        assertThat(resp.author().id()).isNull();
    }

    // ── #2: 비로그인 뷰어는 mine=false ──
    @Test
    void anonymousPost_anonymousViewer_getsMineFalse() {
        when(postMapper.findById(POST_ID)).thenReturn(anonymousPost());
        when(aiResultMapper.findByPostIdAndTaskType(any(), any())).thenReturn(null);

        PostDetailResponse resp = service.getPostDetail(POST_ID, null);

        assertThat(resp.mine()).isFalse();
    }

    // ── #1: 목록이 offset=page*size 로 조회하고 total(전체 건수)을 그대로 내려 Pager 가 전체 페이지를 계산 ──
    @Test
    void getPosts_usesServerPaging_offsetAndTotal() {
        // 임계 높게 — 블러 계산이 단언에 끼어들지 않게.
        when(moderationSettingService.getReportBlurThreshold()).thenReturn(1000);
        when(nicknameProfileService.bulkResolveDisplayNames(any())).thenReturn(Map.of());
        // page=2, size=20 → offset=40. 뷰어 null 이면 차단 필터 생략.
        when(postMapper.findAll(isNull(), eq("PUBLISHED"), eq("latest"), isNull(), eq(40), eq(20), isNull()))
                .thenReturn(List.of(anonymousPost(), anonymousPost()));
        when(postMapper.countAll(isNull(), eq("PUBLISHED"), isNull(), isNull())).thenReturn(250);

        PostPageResponse resp = service.getPosts(null, null, "latest", 2, 20, null);

        assertThat(resp.total()).isEqualTo(250);   // 전체 250건 → Pager 는 13페이지 계산(100 상한 없음)
        assertThat(resp.page()).isEqualTo(2);
        assertThat(resp.size()).isEqualTo(20);
        assertThat(resp.posts()).hasSize(2);
        // 3번째 페이지(0-based 2)는 offset 40 으로 조회됐음을 확정.
        verify(postMapper).findAll(isNull(), eq("PUBLISHED"), eq("latest"), isNull(), eq(40), eq(20), isNull());
    }
}
