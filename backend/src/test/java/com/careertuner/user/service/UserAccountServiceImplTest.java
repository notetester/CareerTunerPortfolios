package com.careertuner.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.careertuner.auth.domain.EmailVerification;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.auth.service.EmailService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.web.FrontendReturnTarget;
import com.careertuner.common.web.FrontendReturnUrlResolver;
import com.careertuner.file.service.FileService;
import com.careertuner.user.domain.User;
import com.careertuner.user.dto.AccountInfoResponse;
import com.careertuner.user.mapper.UserAccountMapper;

import tools.jackson.databind.ObjectMapper;

/**
 * 계정 확충 서비스 단위 검증.
 * - 로그인 아이디: 최초 1회만, 중복 거부, 이미 설정 시 변경 거부
 * - 전화번호: 형식 정규화(하이픈 통일), 중복 거부
 */
class UserAccountServiceImplTest {

    private final UserAccountMapper mapper = mock(UserAccountMapper.class);
    private final AuthMapper authMapper = mock(AuthMapper.class);
    private final EmailService emailService = mock(EmailService.class);
    private final FrontendReturnUrlResolver frontendReturnUrlResolver = mock(FrontendReturnUrlResolver.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final FileService fileService = mock(FileService.class);
    private final UserAccountServiceImpl service = new UserAccountServiceImpl(
            mapper,
            new ObjectMapper(),
            authMapper,
            emailService,
            frontendReturnUrlResolver,
            passwordEncoder,
            fileService);

    private User user(Long id, String loginId, String phone) {
        return User.builder().id(id).email("redacted-826b0cff85484558@example.com").name("홍길동")
                .loginId(loginId).phone(phone).password("encoded")
                .passwordEnabled(true).status("ACTIVE").build();
    }

    @Test
    void deleteOwnAccountAnonymizesThenRevokesEveryAuthenticationAndDeliveryPath() {
        User user = user(1L, "gildong", null);
        when(mapper.findByIdForUpdate(1L)).thenReturn(user);
        when(passwordEncoder.matches("current-password", "encoded")).thenReturn(true);
        when(mapper.anonymizeAndSoftDeleteOwnAccount(1L)).thenReturn(1);

        service.deleteOwnAccount(1L, "current-password", "회원탈퇴");

        InOrder order = inOrder(mapper, authMapper, fileService);
        order.verify(mapper).anonymizeAndSoftDeleteOwnAccount(1L);
        order.verify(authMapper).revokeAllForUser(1L);
        order.verify(mapper).deleteAllSocialLinks(1L);
        order.verify(mapper).deleteAllPushSubscriptions(1L);
        order.verify(mapper).expireAllEmailVerifications(1L);
        order.verify(mapper).deleteAllSmsOtpCodes(1L);
        order.verify(mapper).scrubUserProfile(1L);
        order.verify(mapper).scrubUserProfileVersions(1L);
        order.verify(mapper).scrubProfileAiAnalyses(1L);
        order.verify(mapper).scrubResumeDetail(1L);
        order.verify(mapper).scrubCorrectionRequests(1L);
        order.verify(fileService).deleteOwnedProfileFiles(1L);
        order.verify(mapper).hideAndAnonymizeNicknameProfiles(1L);
        order.verify(mapper).anonymizeChatProfiles(1L);
        order.verify(mapper).clearConversationNicknameProfiles(1L);
        order.verify(mapper).deactivateConversationMemberships(1L);
        order.verify(mapper).cancelPendingFriendRequests(1L);
        order.verify(mapper).cancelPendingConversationInvites(1L);
        order.verify(mapper).deleteDesktopPresence(1L);
    }

    @Test
    void deleteOwnAccountRejectsWrongPasswordBeforeMutation() {
        User user = user(1L, "gildong", null);
        when(mapper.findByIdForUpdate(1L)).thenReturn(user);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteOwnAccount(1L, "wrong", "회원탈퇴"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(mapper, never()).anonymizeAndSoftDeleteOwnAccount(org.mockito.ArgumentMatchers.anyLong());
        verify(authMapper, never()).revokeAllForUser(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void socialOnlyAccountUsesExplicitConfirmationWithoutPassword() {
        User social = user(2L, null, null);
        social.setPasswordEnabled(false);
        social.setPassword(null);
        when(mapper.findByIdForUpdate(2L)).thenReturn(social);
        when(mapper.anonymizeAndSoftDeleteOwnAccount(2L)).thenReturn(1);

        service.deleteOwnAccount(2L, null, "회원탈퇴");

        verify(mapper).anonymizeAndSoftDeleteOwnAccount(2L);
        verify(passwordEncoder, never()).matches(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ── 로그인 아이디 최초 설정 성공(소문자 정규화) ──
    @Test
    void setLoginId_firstTime_persistsLowercased() {
        when(mapper.findById(1L)).thenReturn(user(1L, null, null));
        when(mapper.countByLoginId("gildong")).thenReturn(0);
        when(mapper.setLoginIdIfAbsent(1L, "gildong")).thenReturn(1);
        when(mapper.findLinkedProviders(1L)).thenReturn(List.of());

        service.setLoginId(1L, "GilDong");

        verify(mapper).setLoginIdIfAbsent(eq(1L), eq("gildong"));
    }

    // ── 이미 설정된 아이디는 변경 거부 ──
    @Test
    void setLoginId_alreadySet_rejected() {
        when(mapper.findById(1L)).thenReturn(user(1L, "already", null));

        assertThatThrownBy(() -> service.setLoginId(1L, "newid"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(mapper, never()).setLoginIdIfAbsent(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    // ── 중복 아이디 거부 ──
    @Test
    void setLoginId_duplicate_rejected() {
        when(mapper.findById(1L)).thenReturn(user(1L, null, null));
        when(mapper.countByLoginId("taken")).thenReturn(1);

        assertThatThrownBy(() -> service.setLoginId(1L, "taken"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
    }

    // ── 전화번호 하이픈 없는 입력을 정규화해 저장 ──
    @Test
    void setPhone_normalizesToHyphenated() {
        when(mapper.findById(1L)).thenReturn(user(1L, null, null));
        when(mapper.countByPhone(eq("010-1234-5678"), eq(1L))).thenReturn(0);
        when(mapper.updatePhone(1L, "010-1234-5678")).thenReturn(1);
        when(mapper.findLinkedProviders(1L)).thenReturn(List.of());

        service.setPhone(1L, "01012345678");

        verify(mapper).updatePhone(eq(1L), eq("010-1234-5678"));
    }

    // ── 전화번호 형식 오류 거부 ──
    @Test
    void setPhone_invalidFormat_rejected() {
        when(mapper.findById(1L)).thenReturn(user(1L, null, null));

        assertThatThrownBy(() -> service.setPhone(1L, "123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    // ── 계정 정보 응답: loginIdSet 플래그와 연결 계정 목록 반영 ──
    @Test
    void accountInfo_reflectsFlagsAndProviders() {
        when(mapper.findById(1L)).thenReturn(user(1L, "gildong", "010-1111-2222"));
        when(mapper.findLinkedProviders(1L)).thenReturn(List.of("KAKAO", "GOOGLE"));

        AccountInfoResponse res = service.accountInfo(1L);

        assertThat(res.loginIdSet()).isTrue();
        assertThat(res.phone()).isEqualTo("010-1111-2222");
        assertThat(res.linkedProviders()).containsExactly("KAKAO", "GOOGLE");
    }

    // ── 실제 이메일 등록 요청은 인증 토큰을 만들고 인증 메일을 보낸다 ──
    @Test
    void requestEmailRegistration_createsVerificationAndSendsMail() {
        when(mapper.findById(1L)).thenReturn(user(1L, null, null));
        when(mapper.countByEmailExcludingUser("new@example.com", 1L)).thenReturn(0);
        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);

        FrontendReturnTarget target = new FrontendReturnTarget(
                "sites", "https://careertuner.career-tuner-4654.chatgpt.site");
        service.requestEmailRegistration(1L, " New@Example.COM ", target);

        verify(authMapper).insertEmailVerification(captor.capture());
        EmailVerification verification = captor.getValue();
        assertThat(verification.getUserId()).isEqualTo(1L);
        assertThat(verification.getEmail()).isEqualTo("new@example.com");
        assertThat(verification.getPurpose()).isEqualTo("EMAIL_CHANGE");
        assertThat(verification.getFrontendClient()).isEqualTo("sites");
        assertThat(verification.getToken()).isNotBlank();
        assertThat(verification.getExpiredAt()).isAfter(LocalDateTime.now().plusHours(23));
        verify(emailService).sendVerificationEmail("new@example.com", verification.getToken(), target);
    }

    @Test
    void unlinkSocial_rejectsWhenNoLoginMethodRemains() {
        User socialOnly = User.builder().id(1L).email("kakao_1@social.careertuner")
                .name("소셜").passwordEnabled(false).emailVerified(false).build();
        when(mapper.findByIdForUpdate(1L)).thenReturn(socialOnly);
        when(mapper.findLinkedProviders(1L)).thenReturn(List.of("KAKAO"));

        assertThatThrownBy(() -> service.unlinkSocial(1L, "kakao"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(mapper, never()).deleteSocial(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unlinkSocial_rejectsDuplicateRowsOfSameProviderAsRemainingMethod() {
        User socialOnly = User.builder().id(1L).email("kakao_1@social.careertuner")
                .name("소셜").passwordEnabled(false).emailVerified(false).build();
        when(mapper.findByIdForUpdate(1L)).thenReturn(socialOnly);
        when(mapper.findLinkedProviders(1L)).thenReturn(List.of("KAKAO", "KAKAO"));

        assertThatThrownBy(() -> service.unlinkSocial(1L, "kakao"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(mapper, never()).deleteSocial(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unlinkSocial_allowsWhenPasswordLoginRemains() {
        when(mapper.findByIdForUpdate(1L)).thenReturn(user(1L, "gildong", null));
        when(mapper.findById(1L)).thenReturn(user(1L, "gildong", null));
        when(mapper.findLinkedProviders(1L)).thenReturn(List.of("KAKAO"));

        service.unlinkSocial(1L, "kakao");

        InOrder order = inOrder(mapper);
        order.verify(mapper).findByIdForUpdate(1L);
        order.verify(mapper).findLinkedProviders(1L);
        order.verify(mapper).deleteSocial(1L, "KAKAO");
    }
}
