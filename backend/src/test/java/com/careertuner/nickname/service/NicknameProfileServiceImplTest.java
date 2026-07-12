package com.careertuner.nickname.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.nickname.domain.ConversationMemberProfile;
import com.careertuner.nickname.domain.AccountNameRow;
import com.careertuner.nickname.domain.NicknameProfile;
import com.careertuner.nickname.dto.ConversationProfileRequest;
import com.careertuner.nickname.dto.ConversationProfileResponse;
import com.careertuner.nickname.dto.DisplayNameResponse;
import com.careertuner.nickname.dto.NicknameProfileRequest;
import com.careertuner.nickname.dto.NicknameProfileResponse;
import com.careertuner.nickname.mapper.NicknameProfileMapper;

/**
 * 복수 닉네임 프로필 서비스 단위 검증.
 * - 첫 프로필 자동 기본, 닉네임 전역 중복 거부, 마지막 프로필 삭제 금지
 * - 채팅방 프로필: 멤버 검증, 프로필 지정 시 익명 배제, 미지정 시 익명 참가
 * - 표시명 해석: 프로필 소유 검증 + 폴백(기본 프로필 → 계정명)
 */
class NicknameProfileServiceImplTest {

    private final NicknameProfileMapper mapper = mock(NicknameProfileMapper.class);
    private final NicknameProfileServiceImpl service = new NicknameProfileServiceImpl(mapper);

    private NicknameProfile profile(Long id, Long userId, String nickname, boolean isDefault) {
        return NicknameProfile.builder()
                .id(id).userId(userId).nickname(nickname).isDefault(isDefault).status("ACTIVE").build();
    }

    // ── 첫 프로필은 자동 기본(is_default=1) ──
    @Test
    void create_firstProfile_becomesDefault() {
        when(mapper.countByUserId(1L)).thenReturn(0);
        when(mapper.countByNicknameExcluding(eq("길동"), isNull())).thenReturn(0);
        when(mapper.findById(any())).thenReturn(profile(5L, 1L, "길동", true));

        service.create(1L, new NicknameProfileRequest("길동", null, null));

        ArgumentCaptor<NicknameProfile> cap = ArgumentCaptor.forClass(NicknameProfile.class);
        verify(mapper).insert(cap.capture());
        assertThat(cap.getValue().isDefault()).isTrue();
    }

    // ── 두 번째 프로필은 기본 아님 ──
    @Test
    void create_secondProfile_notDefault() {
        when(mapper.countByUserId(1L)).thenReturn(1);
        when(mapper.countByNicknameExcluding(eq("부캐"), isNull())).thenReturn(0);
        when(mapper.findById(any())).thenReturn(profile(6L, 1L, "부캐", false));

        service.create(1L, new NicknameProfileRequest("부캐", null, null));

        ArgumentCaptor<NicknameProfile> cap = ArgumentCaptor.forClass(NicknameProfile.class);
        verify(mapper).insert(cap.capture());
        assertThat(cap.getValue().isDefault()).isFalse();
    }

    // ── 닉네임 전역 중복 거부 ──
    @Test
    void create_duplicateNickname_rejected() {
        when(mapper.countByUserId(1L)).thenReturn(1);
        when(mapper.countByNicknameExcluding(eq("중복"), isNull())).thenReturn(1);

        assertThatThrownBy(() -> service.create(1L, new NicknameProfileRequest("중복", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(mapper, never()).insert(any());
    }

    // ── 마지막 남은 프로필 삭제 금지 ──
    @Test
    void delete_lastProfile_rejected() {
        when(mapper.findById(5L)).thenReturn(profile(5L, 1L, "길동", true));
        when(mapper.countByUserId(1L)).thenReturn(1);

        assertThatThrownBy(() -> service.delete(1L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(mapper, never()).hide(anyLong(), anyLong());
    }

    // ── 타인 소유 프로필 접근 시 NOT_FOUND(귀속은 계정 단위) ──
    @Test
    void update_notOwned_rejected() {
        when(mapper.findById(9L)).thenReturn(profile(9L, 2L, "남", false));

        assertThatThrownBy(() -> service.update(1L, 9L, new NicknameProfileRequest("새이름", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ── 채팅방 프로필: 프로필 지정 시 익명 배제 ──
    @Test
    void setConversationProfile_withProfile_notAnonymous() {
        when(mapper.isActiveMember(100L, 1L)).thenReturn(1);
        when(mapper.findById(5L)).thenReturn(profile(5L, 1L, "길동", true));

        ConversationProfileResponse res = service.setConversationProfile(1L, 100L,
                new ConversationProfileRequest(5L, true)); // anonymous 요청은 무시되어야

        assertThat(res.anonymous()).isFalse();
        assertThat(res.nickname()).isEqualTo("길동");
        ArgumentCaptor<ConversationMemberProfile> cap = ArgumentCaptor.forClass(ConversationMemberProfile.class);
        verify(mapper).upsertConversationProfile(cap.capture());
        assertThat(cap.getValue().isAnonymous()).isFalse();
        assertThat(cap.getValue().getNicknameProfileId()).isEqualTo(5L);
    }

    // ── 채팅방 프로필: 미지정 시 익명 참가 ──
    @Test
    void setConversationProfile_noProfile_anonymous() {
        when(mapper.isActiveMember(100L, 1L)).thenReturn(1);

        ConversationProfileResponse res = service.setConversationProfile(1L, 100L,
                new ConversationProfileRequest(null, false));

        assertThat(res.anonymous()).isTrue();
        assertThat(res.nickname()).isEqualTo("익명");
        ArgumentCaptor<ConversationMemberProfile> cap = ArgumentCaptor.forClass(ConversationMemberProfile.class);
        verify(mapper).upsertConversationProfile(cap.capture());
        assertThat(cap.getValue().isAnonymous()).isTrue();
        assertThat(cap.getValue().getNicknameProfileId()).isNull();
    }

    // ── 채팅방 프로필: 비멤버 거부 ──
    @Test
    void setConversationProfile_notMember_forbidden() {
        when(mapper.isActiveMember(100L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> service.setConversationProfile(1L, 100L,
                new ConversationProfileRequest(null, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(mapper, never()).upsertConversationProfile(any());
    }

    // ── 표시명 해석: 지정 프로필이 그 계정 소유면 그대로 사용 ──
    @Test
    void resolveDisplayName_ownedProfile_used() {
        when(mapper.findById(5L)).thenReturn(profile(5L, 1L, "길동", true));

        DisplayNameResponse res = service.resolveDisplayName(1L, 5L);

        assertThat(res.accountId()).isEqualTo(1L);
        assertThat(res.nicknameProfileId()).isEqualTo(5L);
        assertThat(res.displayName()).isEqualTo("길동");
    }

    // ── 표시명 해석: 지정 프로필이 타 계정 소유면 폴백(기본 프로필) ──
    @Test
    void resolveDisplayName_foreignProfile_fallsBackToDefault() {
        when(mapper.findById(9L)).thenReturn(profile(9L, 2L, "남", false)); // 다른 계정 소유
        when(mapper.findDefaultByUserId(1L)).thenReturn(profile(3L, 1L, "내기본", true));

        DisplayNameResponse res = service.resolveDisplayName(1L, 9L);

        assertThat(res.nicknameProfileId()).isEqualTo(3L);
        assertThat(res.displayName()).isEqualTo("내기본");
    }

    // ── 표시명 해석: 프로필 없으면 계정명 폴백 ──
    @Test
    void resolveDisplayName_noProfile_fallsBackToAccountName() {
        when(mapper.findDefaultByUserId(1L)).thenReturn(null);
        when(mapper.findAccountName(1L)).thenReturn("홍길동");

        DisplayNameResponse res = service.resolveDisplayName(1L, null);

        assertThat(res.nicknameProfileId()).isNull();
        assertThat(res.displayName()).isEqualTo("홍길동");
    }

    @Test
    void resolveDisplayName_deletedAccountReturnsUnlinkableTombstone() {
        when(mapper.findAccountStatus(1L)).thenReturn("DELETED");

        DisplayNameResponse res = service.resolveDisplayName(1L, 5L);

        assertThat(res.accountId()).isNull();
        assertThat(res.nicknameProfileId()).isNull();
        assertThat(res.displayName()).isEqualTo("탈퇴한 사용자");
        assertThat(res.avatarFileId()).isNull();
        verify(mapper, never()).findById(5L);
    }

    @Test
    void bulkResolveDeletedAccountReturnsUnlinkableTombstone() {
        var query = new com.careertuner.nickname.dto.DisplayNameQuery(1L, null);
        when(mapper.findDefaultsByUserIds(any())).thenReturn(java.util.List.of());
        when(mapper.findAccountNames(any())).thenReturn(java.util.List.of(
                AccountNameRow.builder().userId(1L).name("탈퇴한 사용자").status("DELETED").build()));

        DisplayNameResponse res = service.bulkResolveDisplayNames(java.util.List.of(query)).get(query);

        assertThat(res.accountId()).isNull();
        assertThat(res.nicknameProfileId()).isNull();
        assertThat(res.displayName()).isEqualTo("탈퇴한 사용자");
        assertThat(res.avatarFileId()).isNull();
    }

    // ── myProfiles: 프로필이 없으면 기본 프로필을 자동 생성 후 반환 ──
    @Test
    void myProfiles_autoCreatesDefaultWhenEmpty() {
        when(mapper.countByUserId(1L)).thenReturn(0);
        when(mapper.findAccountName(1L)).thenReturn("홍길동");
        when(mapper.countByNicknameExcluding(any(), isNull())).thenReturn(0);
        when(mapper.findByUserId(1L)).thenReturn(java.util.List.of(profile(1L, 1L, "홍길동", true)));

        java.util.List<NicknameProfileResponse> result = service.myProfiles(1L);

        verify(mapper).insert(any());
        assertThat(result).hasSize(1);
    }
}
