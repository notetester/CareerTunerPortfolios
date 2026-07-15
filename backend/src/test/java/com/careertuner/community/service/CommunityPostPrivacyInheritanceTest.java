package com.careertuner.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.privacy.domain.UserBlock;
import com.careertuner.privacy.domain.UserPrivacyPolicy;
import com.careertuner.privacy.domain.UserRoleRow;
import com.careertuner.privacy.mapper.PrivacyMapper;
import com.careertuner.privacy.service.PrivacyPolicyServiceImpl;
import com.careertuner.privacy.service.PrivacySurfaces;

import tools.jackson.databind.ObjectMapper;

/** 커뮤니티 content.post 표면이 개인정책 정본의 상속·익명·운영자 예외를 그대로 쓰는지 고정한다. */
class CommunityPostPrivacyInheritanceTest {

    private static final long VIEWER = 1L;

    private PrivacyMapper mapper;
    private PrivacyPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(PrivacyMapper.class);
        service = new PrivacyPolicyServiceImpl(mapper, new ObjectMapper(), "test-salt");
    }

    @Test
    void nullExplicitFlagInheritsBlockedAccountAllowAndOperatorRemainsVisible() {
        Set<Long> targets = Set.of(2L, 3L);
        UserBlock inherited = UserBlock.builder()
                .userId(VIEWER).blockedUserId(2L).flagsJson(null).build();
        UserBlock operator = UserBlock.builder()
                .userId(VIEWER).blockedUserId(3L).flagsJson(null).build();
        when(mapper.findBlocksAmong(VIEWER, targets)).thenReturn(List.of(inherited, operator));
        when(mapper.findIpBlockedAmong(VIEWER, targets, "test-salt")).thenReturn(List.of());
        when(mapper.findFriendsAmong(VIEWER, targets)).thenReturn(List.of());
        when(mapper.findRolesAmong(targets)).thenReturn(List.of(
                new UserRoleRow(2L, "USER"),
                new UserRoleRow(3L, "SUPER_ADMIN")));
        when(mapper.findPolicy(VIEWER)).thenReturn(UserPrivacyPolicy.builder()
                .userId(VIEWER)
                .policyJson("{\"relations\":{\"blockedAccount\":{\"content.post\":\"allow\"}}}")
                .build());

        assertThat(service.blockedAuthorsAmong(VIEWER, targets, PrivacySurfaces.CONTENT_POST)).isEmpty();
    }

    @Test
    void explicitAllowOverridesBlockedAccountDefaultBlock() {
        Set<Long> targets = Set.of(2L);
        UserBlock allowed = UserBlock.builder()
                .userId(VIEWER).blockedUserId(2L)
                .flagsJson("{\"content.post\":\"allow\"}")
                .build();
        when(mapper.findBlocksAmong(VIEWER, targets)).thenReturn(List.of(allowed));
        when(mapper.findIpBlockedAmong(VIEWER, targets, "test-salt")).thenReturn(List.of());
        when(mapper.findFriendsAmong(VIEWER, targets)).thenReturn(List.of());
        when(mapper.findRolesAmong(targets)).thenReturn(List.of(new UserRoleRow(2L, "USER")));
        when(mapper.findPolicy(VIEWER)).thenReturn(null);

        assertThat(service.blockedAuthorsAmong(VIEWER, targets, PrivacySurfaces.CONTENT_POST)).isEmpty();
    }

    @Test
    void anonymousOverrideIsIndependentFromNamedPostSurface() {
        Set<Long> targets = Set.of(2L);
        UserBlock block = UserBlock.builder()
                .userId(VIEWER).blockedUserId(2L)
                .flagsJson("{\"content.post\":\"block\",\"content.post.anonymous\":\"allow\"}")
                .build();
        when(mapper.findBlocksAmong(VIEWER, targets)).thenReturn(List.of(block));
        when(mapper.findIpBlockedAmong(VIEWER, targets, "test-salt")).thenReturn(List.of());
        when(mapper.findFriendsAmong(VIEWER, targets)).thenReturn(List.of());
        when(mapper.findRolesAmong(targets)).thenReturn(List.of(new UserRoleRow(2L, "USER")));
        when(mapper.findPolicy(VIEWER)).thenReturn(null);

        assertThat(service.blockedAuthorsAmong(VIEWER, targets, PrivacySurfaces.CONTENT_POST))
                .containsExactly(2L);
        assertThat(service.blockedAuthorsAmong(
                VIEWER, targets, PrivacySurfaces.CONTENT_POST + ".anonymous"))
                .isEmpty();
    }
}
