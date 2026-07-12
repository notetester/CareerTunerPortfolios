package com.careertuner.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostReaction;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.CommunitySubscriptionMapper;
import com.careertuner.community.mapper.CommunityTagMapper;
import com.careertuner.community.mapper.PostScrapMapper;
import com.careertuner.community.mapper.ReactionMapper;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.community.moderation.service.ModerationSettingService;
import com.careertuner.nickname.service.NicknameProfileService;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.privacy.service.PrivacyPolicyService;
import com.careertuner.reward.service.RewardService;

import tools.jackson.databind.ObjectMapper;

class CommunityDeletedIdentityResponseTest {

    @Test
    void postListKeepsDeletedUsersPostButRemovesAuthorLinks() {
        CommunityPostMapper postMapper = mock(CommunityPostMapper.class);
        NicknameProfileService nicknameProfileService = mock(NicknameProfileService.class);
        ModerationSettingService moderationSettingService = mock(ModerationSettingService.class);
        CommunityPostServiceImpl service = new CommunityPostServiceImpl(
                postMapper,
                mock(CommunityTagMapper.class),
                mock(ReactionMapper.class),
                mock(PostScrapMapper.class),
                mock(CommunitySubscriptionMapper.class),
                new ObjectMapper(),
                mock(ApplicationEventPublisher.class),
                mock(PostAiResultMapper.class),
                mock(PrivacyPolicyService.class),
                nicknameProfileService,
                mock(PersonalizedFeedService.class),
                moderationSettingService,
                mock(RewardService.class));
        CommunityPost deletedAuthorPost = CommunityPost.builder()
                .id(1L)
                .userId(44L)
                .category("FREE")
                .title("보존된 글")
                .content("본문")
                .status(PostStatus.PUBLISHED.name())
                .userName("탈퇴한 사용자")
                .userStatus("DELETED")
                .nicknameProfileId(777L)
                .reportCount(0)
                .createdAt(LocalDateTime.now())
                .build();
        when(postMapper.findAll(null, PostStatus.PUBLISHED.name(), "latest", null, 0, 20, null))
                .thenReturn(List.of(deletedAuthorPost));
        when(postMapper.countAll(null, PostStatus.PUBLISHED.name(), null, null)).thenReturn(1);
        when(nicknameProfileService.bulkResolveDisplayNames(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());
        when(moderationSettingService.getReportBlurThreshold()).thenReturn(3);

        var author = service.getPosts(null, null, "latest", 0, 20, null).posts().getFirst().author();

        assertThat(author.name()).isEqualTo("탈퇴한 사용자");
        assertThat(author.id()).isNull();
        assertThat(author.nicknameProfileId()).isNull();
    }

    @Test
    void reactorListKeepsAuditCountButRemovesDeletedUserLink() {
        ReactionMapper reactionMapper = mock(ReactionMapper.class);
        CommunityPostMapper postMapper = mock(CommunityPostMapper.class);
        ReactionServiceImpl service = new ReactionServiceImpl(
                reactionMapper,
                postMapper,
                mock(CommunityCommentMapper.class),
                mock(NotificationService.class));
        when(postMapper.findById(1L)).thenReturn(CommunityPost.builder()
                .id(1L).userId(9L).status(PostStatus.PUBLISHED.name()).build());
        when(reactionMapper.findPostReactors(1L, null)).thenReturn(List.of(
                PostReaction.builder()
                        .id(8L).userId(44L).postId(1L).reactionType("LIKE")
                        .userName("탈퇴한 사용자").userStatus("DELETED")
                        .createdAt(LocalDateTime.now()).build()));

        var response = service.getPostReactors(1L, null).getFirst();

        assertThat(response.name()).isEqualTo("탈퇴한 사용자");
        assertThat(response.userId()).isNull();
        assertThat(response.anonymous()).isFalse();
    }
}
