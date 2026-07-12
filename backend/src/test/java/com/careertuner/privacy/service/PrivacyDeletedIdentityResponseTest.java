package com.careertuner.privacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.privacy.domain.UserBlock;
import com.careertuner.privacy.domain.UserIpBlock;
import com.careertuner.privacy.mapper.PrivacyMapper;

import tools.jackson.databind.ObjectMapper;

class PrivacyDeletedIdentityResponseTest {

    @Test
    void userAndIpBlockListsPreserveBlockRowsWithoutDeletedAccountLinks() {
        PrivacyMapper mapper = mock(PrivacyMapper.class);
        PrivacyPolicyServiceImpl service = new PrivacyPolicyServiceImpl(
                mapper, new ObjectMapper(), "test-salt");
        when(mapper.findBlocksByUser(1L)).thenReturn(List.of(UserBlock.builder()
                .id(10L)
                .userId(1L)
                .blockedUserId(44L)
                .blockedUserName("탈퇴한 사용자")
                .blockedUserEmail(null)
                .blockedUserStatus("DELETED")
                .createdAt(LocalDateTime.now())
                .build()));
        when(mapper.findIpBlocksByUser(1L)).thenReturn(List.of(UserIpBlock.builder()
                .id(20L)
                .userId(1L)
                .sourceUserId(44L)
                .sourceUserName("탈퇴한 사용자")
                .sourceUserStatus("DELETED")
                .label("차단 IP #20")
                .createdAt(LocalDateTime.now())
                .build()));

        var block = service.listUserBlocks(1L).getFirst();
        var ipBlock = service.listIpBlocks(1L).getFirst();

        assertThat(block.id()).isEqualTo(10L);
        assertThat(block.blockedUserId()).isNull();
        assertThat(block.blockedUserName()).isEqualTo("탈퇴한 사용자");
        assertThat(block.blockedUserEmail()).isNull();
        assertThat(ipBlock.id()).isEqualTo(20L);
        assertThat(ipBlock.sourceUserId()).isNull();
        assertThat(ipBlock.sourceUserName()).isEqualTo("탈퇴한 사용자");
    }
}
