package com.careertuner.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.careertuner.auth.domain.EmailVerification;
import com.careertuner.auth.domain.UserSocial;
import com.careertuner.activitylog.service.SecurityHistoryService;
import com.careertuner.auth.dto.LoginRequest;
import com.careertuner.auth.dto.OAuthCallbackResult;
import com.careertuner.auth.dto.PasswordResetRequest;
import com.careertuner.auth.dto.RegisterRequest;
import com.careertuner.auth.dto.TokenResponse;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.JwtTokenProvider;
import com.careertuner.common.security.JwtTokenProvider.OauthState;
import com.careertuner.loginrisk.service.LoginRiskPolicyService;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

/**
 * 로그인 실패 자동 잠금 정책의 <b>토글 3상태</b> 회귀 테스트.
 *
 * <p>사용자 요구(직접 검증): OFF=무제약, ON 기본값(5회)=기존 상수와 동일, ON 변경값=의도대로.
 * 잠금은 {@code userMapper.lockForFailedLogin} 호출 여부로 판정한다.</p>
 */
class AuthServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuthMapper authMapper = mock(AuthMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final EmailService emailService = mock(EmailService.class);
    private final SocialOAuthService socialOAuthService = mock(SocialOAuthService.class);
    private final CareerTunerProperties props = mock(CareerTunerProperties.class);
    private final SecurityHistoryService securityHistoryService = mock(SecurityHistoryService.class);
    private final LoginRiskPolicyService loginRiskPolicyService = mock(LoginRiskPolicyService.class);
    private final com.careertuner.reward.service.RewardService rewardService =
            mock(com.careertuner.reward.service.RewardService.class);

    private final AuthServiceImpl service = new AuthServiceImpl(
            userMapper, authMapper, passwordEncoder, jwtTokenProvider, emailService,
            socialOAuthService, props, securityHistoryService, loginRiskPolicyService, rewardService);

    private static final String EMAIL = "user@test.com";

    @BeforeEach
    void defaultPolicy() {
        when(loginRiskPolicyService.isLockoutEnabled()).thenReturn(false);
    }

    @Test
    void register_withLoginIdOnly_skipsEmailVerificationAndLogsLoginIdLogin() {
        var jwt = new CareerTunerProperties.Jwt();
        when(props.getJwt()).thenReturn(jwt);
        when(userMapper.countByLoginId("career_user")).thenReturn(0);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        org.mockito.Mockito.doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return null;
        }).when(userMapper).insert(any(User.class));
        when(jwtTokenProvider.createAccessToken(10L, null, "USER")).thenReturn("access-token");
        when(jwtTokenProvider.getAccessValiditySeconds()).thenReturn(1800L);

        TokenResponse response = service.register(new RegisterRequest(
                null, "career_user", "password123", "테스터", true, true, false, false), null);

        assertThat(response.accessToken()).isEqualTo("access-token");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isNull();
        assertThat(userCaptor.getValue().getLoginId()).isEqualTo("career_user");
        verify(authMapper, never()).insertEmailVerification(any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
        verify(authMapper).insertLoginHistory(org.mockito.ArgumentMatchers.argThat(history ->
                "LOGIN_ID".equals(history.getLoginMethod()) && "career_user".equals(history.getLoginIdentifier())));
    }

    /** ACTIVE 계정에서 틀린 비밀번호로 로그인 시도 → 항상 invalidLogin 예외. failedCount 는 "이번 실패 직전"의 누적. */
    private void attemptWrongPassword(int priorFailedCount) {
        when(userMapper.findByLoginIdentifier(EMAIL)).thenReturn(User.builder()
                .id(1L).email(EMAIL).password("hash").passwordEnabled(true)
                .status("ACTIVE").failedLoginCount(priorFailedCount).build());
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "wrong"), null))
                .isInstanceOf(BusinessException.class);
    }

    // ── OFF: 무제약 — 실패가 아무리 쌓여도 잠그지 않는다(집계는 유지) ──
    @Test
    void off_neverLocks_evenBeyondThreshold() {
        when(loginRiskPolicyService.isLockoutEnabled()).thenReturn(false);
        attemptWrongPassword(99);
        verify(userMapper).increaseFailedLogin(1L);                               // 실패 집계는 유지
        verify(userMapper, never()).lockForFailedLogin(anyLong(), any(), any());  // 잠금 없음
    }

    // ── ON 기본값(5회) = 기존 상수와 동일: 5번째 실패에 잠금 ──
    @Test
    void on_default5_locksAtFifthFailure() {
        when(loginRiskPolicyService.isLockoutEnabled()).thenReturn(true);
        when(loginRiskPolicyService.getMaxFailedCount()).thenReturn(5);
        when(loginRiskPolicyService.getLockMinutes()).thenReturn(10);
        attemptWrongPassword(4);                                                  // 4 + 1 = 5 → 잠금
        verify(userMapper).lockForFailedLogin(eq(1L), any(), any());
    }

    // ── ON 기본값(5회): 임계 미만이면 잠그지 않는다 ──
    @Test
    void on_default5_doesNotLockBeforeThreshold() {
        when(loginRiskPolicyService.isLockoutEnabled()).thenReturn(true);
        when(loginRiskPolicyService.getMaxFailedCount()).thenReturn(5);
        attemptWrongPassword(3);                                                  // 3 + 1 = 4 < 5 → 잠금 없음
        verify(userMapper, never()).lockForFailedLogin(anyLong(), any(), any());
    }

    // ── ON 변경값(3회): 의도대로 3번째 실패에 잠금 ──
    @Test
    void on_custom3_locksAtThirdFailure() {
        when(loginRiskPolicyService.isLockoutEnabled()).thenReturn(true);
        when(loginRiskPolicyService.getMaxFailedCount()).thenReturn(3);
        when(loginRiskPolicyService.getLockMinutes()).thenReturn(30);
        attemptWrongPassword(2);                                                  // 2 + 1 = 3 → 잠금
        verify(userMapper).lockForFailedLogin(eq(1L), any(), any());
    }

    @Test
    void emailLogin_requiresVerifiedEmailAfterPasswordMatches() {
        when(userMapper.findByLoginIdentifier(EMAIL)).thenReturn(User.builder()
                .id(1L).email(EMAIL).password("hash").passwordEnabled(true)
                .emailVerified(false).status("ACTIVE").build());
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "pw"), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(com.careertuner.common.exception.ErrorCode.FORBIDDEN);
        verify(userMapper, never()).touchLastLoginAndResetFailures(1L);
    }

    @Test
    void requestPasswordReset_unknownIdentifier_isSilent() {
        when(userMapper.findByLoginIdentifier("missing")).thenReturn(null);

        service.requestPasswordReset(new PasswordResetRequest(null, "missing"), null);

        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void requestFindId_verifiedLoginId_issuesMaskedLinkToken() {
        when(userMapper.findByEmail(EMAIL)).thenReturn(User.builder()
                .id(1L).email(EMAIL).loginId("career_user").emailVerified(true).status("ACTIVE").build());
        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);

        service.requestFindId(EMAIL, null);

        verify(authMapper).expireUnusedEmailVerifications(EMAIL, "FIND_ID");
        verify(authMapper).insertEmailVerification(captor.capture());
        EmailVerification verification = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(verification.getPurpose()).isEqualTo("FIND_ID");
        verify(emailService).sendFindIdEmail(eq(EMAIL), eq(verification.getToken()));
    }

    @Test
    void handleOAuthCallback_linkState_connectsSocialToCurrentUser() {
        when(socialOAuthService.isSupported("KAKAO")).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("state", "KAKAO"))
                .thenReturn(new OauthState("oauth_link_state", "KAKAO", 1L));
        when(socialOAuthService.fetchUserInfo("KAKAO", "code", "state"))
                .thenReturn(new SocialUserInfo("KAKAO", "kakao-user", "user@test.com", "소셜"));
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).email(EMAIL).status("ACTIVE").build());
        when(authMapper.findSocial("KAKAO", "kakao-user")).thenReturn(null);
        when(authMapper.findSocialByUserAndProvider(1L, "KAKAO")).thenReturn(null);
        ArgumentCaptor<UserSocial> captor = ArgumentCaptor.forClass(UserSocial.class);

        OAuthCallbackResult result = service.handleOAuthCallback("kakao", "code", "state", null);

        assertThat(result.linked()).isTrue();
        assertThat(result.provider()).isEqualTo("KAKAO");
        verify(authMapper).insertSocial(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getProvider()).isEqualTo("KAKAO");
        assertThat(captor.getValue().getProviderUserId()).isEqualTo("kakao-user");
    }

    @Test
    void handleOAuthCallback_linkState_rejectsSocialAlreadyLinkedToAnotherUser() {
        when(socialOAuthService.isSupported("KAKAO")).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("state", "KAKAO"))
                .thenReturn(new OauthState("oauth_link_state", "KAKAO", 1L));
        when(socialOAuthService.fetchUserInfo("KAKAO", "code", "state"))
                .thenReturn(new SocialUserInfo("KAKAO", "kakao-user", "user@test.com", "소셜"));
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).email(EMAIL).status("ACTIVE").build());
        when(authMapper.findSocial("KAKAO", "kakao-user"))
                .thenReturn(UserSocial.builder().userId(2L).provider("KAKAO").providerUserId("kakao-user").build());

        assertThatThrownBy(() -> service.handleOAuthCallback("kakao", "code", "state", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(authMapper, never()).insertSocial(any());
    }

    @Test
    void buildSocialLinkUrl_whenProviderIsNotConfigured_returnsMockCallbackUrl() {
        when(socialOAuthService.isSupported("KAKAO")).thenReturn(true);
        when(socialOAuthService.isConfigured("KAKAO")).thenReturn(false);
        when(socialOAuthService.isMockEnabled()).thenReturn(true);
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).email(EMAIL).status("ACTIVE").build());
        when(jwtTokenProvider.createOauthLinkState("KAKAO", 1L)).thenReturn("signed-state");
        when(socialOAuthService.getMockAuthorizationUrl("KAKAO", "signed-state"))
                .thenReturn("http://localhost:8080/api/auth/oauth/kakao/mock-callback?state=signed-state");

        String url = service.buildSocialLinkUrl(1L, "kakao");

        assertThat(url).contains("/api/auth/oauth/kakao/mock-callback");
    }

    @Test
    void buildSocialLinkUrl_whenProviderIsNotConfiguredAndMockDisabled_failsAsUnavailable() {
        when(socialOAuthService.isSupported("KAKAO")).thenReturn(true);
        when(socialOAuthService.isConfigured("KAKAO")).thenReturn(false);
        when(socialOAuthService.isMockEnabled()).thenReturn(false);
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).email(EMAIL).status("ACTIVE").build());
        when(jwtTokenProvider.createOauthLinkState("KAKAO", 1L)).thenReturn("signed-state");

        assertThatThrownBy(() -> service.buildSocialLinkUrl(1L, "kakao"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void handleOAuthMockCallback_linkState_connectsMockSocialToCurrentUser() {
        when(socialOAuthService.isSupported("NAVER")).thenReturn(true);
        when(socialOAuthService.isMockEnabled()).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("state", "NAVER"))
                .thenReturn(new OauthState("oauth_link_state", "NAVER", 1L));
        when(socialOAuthService.mockUserInfo("NAVER", 1L))
                .thenReturn(new SocialUserInfo("NAVER", "mock-link-1", null, "네이버 mock 사용자"));
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).email(EMAIL).status("ACTIVE").build());
        when(authMapper.findSocial("NAVER", "mock-link-1")).thenReturn(null);
        when(authMapper.findSocialByUserAndProvider(1L, "NAVER")).thenReturn(null);
        ArgumentCaptor<UserSocial> captor = ArgumentCaptor.forClass(UserSocial.class);

        OAuthCallbackResult result = service.handleOAuthMockCallback("naver", "state", null);

        assertThat(result.linked()).isTrue();
        verify(authMapper).insertSocial(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("NAVER");
        assertThat(captor.getValue().getProviderUserId()).isEqualTo("mock-link-1");
    }
}
