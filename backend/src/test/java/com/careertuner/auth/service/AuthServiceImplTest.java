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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.careertuner.auth.domain.EmailVerification;
import com.careertuner.auth.domain.NativeAuthHandoff;
import com.careertuner.auth.domain.RefreshToken;
import com.careertuner.auth.domain.UserSocial;
import com.careertuner.activitylog.service.SecurityHistoryService;
import com.careertuner.auth.dto.LoginRequest;
import com.careertuner.auth.dto.OAuthCallbackResult;
import com.careertuner.auth.dto.OAuthCallbackContext;
import com.careertuner.auth.dto.PasswordResetRequest;
import com.careertuner.auth.dto.RegisterRequest;
import com.careertuner.auth.dto.TokenResponse;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.JwtTokenProvider;
import com.careertuner.common.security.JwtTokenProvider.OauthState;
import com.careertuner.common.web.FrontendReturnTarget;
import com.careertuner.common.web.FrontendReturnUrlResolver;
import com.careertuner.loginrisk.service.LoginRiskPolicyService;
import com.careertuner.notification.mapper.PushSubscriptionMapper;
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
    private final MfaService mfaService = mock(MfaService.class);
    private final CareerTunerProperties props = mock(CareerTunerProperties.class);
    private final FrontendReturnUrlResolver frontendReturnUrlResolver = mock(FrontendReturnUrlResolver.class);
    private final SecurityHistoryService securityHistoryService = mock(SecurityHistoryService.class);
    private final LoginRiskPolicyService loginRiskPolicyService = mock(LoginRiskPolicyService.class);
    private final com.careertuner.reward.service.RewardService rewardService =
            mock(com.careertuner.reward.service.RewardService.class);
    private final PushSubscriptionMapper pushSubscriptionMapper = mock(PushSubscriptionMapper.class);

    private final AuthServiceImpl service = new AuthServiceImpl(
            userMapper, authMapper, passwordEncoder, jwtTokenProvider, emailService,
            socialOAuthService, mfaService, props, frontendReturnUrlResolver,
            securityHistoryService, loginRiskPolicyService, rewardService, pushSubscriptionMapper);

    private static final String EMAIL = "user@test.com";
    private static final String HANDOFF_VERIFIER = "v".repeat(43);
    private static final String HANDOFF_CHALLENGE = sha256Base64Url(HANDOFF_VERIFIER);

    @BeforeEach
    void defaultPolicy() {
        when(loginRiskPolicyService.isLockoutEnabled()).thenReturn(false);
        when(frontendReturnUrlResolver.primary())
                .thenReturn(new FrontendReturnTarget("primary", "https://careertuner.example.com"));
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
                null, "career_user", "password123", "테스터", true, true, false, false, false), null);

        assertThat(response.accessToken()).isEqualTo("access-token");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isNull();
        assertThat(userCaptor.getValue().getLoginId()).isEqualTo("career_user");
        verify(authMapper, never()).insertEmailVerification(any());
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
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
    void emailLogin_allowsUnverifiedEmailAfterPasswordMatches() {
        var jwt = new CareerTunerProperties.Jwt();
        when(userMapper.findByLoginIdentifier(EMAIL)).thenReturn(User.builder()
                .id(1L).email(EMAIL).password("hash").passwordEnabled(true)
                .emailVerified(false).status("ACTIVE").role("USER").build());
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(props.getJwt()).thenReturn(jwt);
        when(jwtTokenProvider.createAccessToken(1L, EMAIL, "USER")).thenReturn("access-token");
        when(jwtTokenProvider.getAccessValiditySeconds()).thenReturn(1800L);

        var response = service.login(new LoginRequest(EMAIL, "pw"), null);

        assertThat(response.mfaRequired()).isFalse();
        assertThat(response.token().accessToken()).isEqualTo("access-token");
        assertThat(response.token().user().emailVerified()).isFalse();
        verify(userMapper).touchLastLoginAndResetFailures(1L);
    }

    @Test
    void requestPasswordReset_unknownIdentifier_isSilent() {
        when(userMapper.findByLoginIdentifier("missing")).thenReturn(null);

        service.requestPasswordReset(new PasswordResetRequest(null, "missing"), null);

        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
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
        verify(emailService).sendFindIdEmail(eq(EMAIL), eq(verification.getToken()),
                eq(new FrontendReturnTarget("primary", "https://careertuner.example.com")));
    }

    @Test
    void verifyEmailResult_keepsSitesClientEvenWhenTokenExpired() {
        EmailVerification verification = EmailVerification.builder()
                .id(1L)
                .userId(1L)
                .email(EMAIL)
                .purpose("VERIFY")
                .frontendClient("sites")
                .expiredAt(java.time.LocalDateTime.now().minusMinutes(1))
                .build();
        when(authMapper.findEmailVerificationByToken("expired-token")).thenReturn(verification);

        var result = service.verifyEmailResult("expired-token");

        assertThat(result.success()).isFalse();
        assertThat(result.frontendClient()).isEqualTo("sites");
    }

    @Test
    void handleOAuthCallback_linkState_connectsSocialToCurrentUser() {
        when(socialOAuthService.isSupported("KAKAO")).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("state", "KAKAO"))
                .thenReturn(new OauthState("oauth_link_state", "KAKAO", 1L, "sites"));
        when(socialOAuthService.fetchUserInfo("KAKAO", "code", "state"))
                .thenReturn(new SocialUserInfo("KAKAO", "kakao-user", "user@test.com", "소셜", true));
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).email(EMAIL).status("ACTIVE").build());
        when(authMapper.findSocial("KAKAO", "kakao-user")).thenReturn(null);
        when(authMapper.findSocialByUserAndProvider(1L, "KAKAO")).thenReturn(null);
        ArgumentCaptor<UserSocial> captor = ArgumentCaptor.forClass(UserSocial.class);

        OAuthCallbackResult result = service.handleOAuthCallback("kakao", "code", "state", null);

        assertThat(result.linked()).isTrue();
        assertThat(result.provider()).isEqualTo("KAKAO");
        assertThat(result.frontendClient()).isEqualTo("sites");
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
                .thenReturn(new SocialUserInfo("KAKAO", "kakao-user", "user@test.com", "소셜", true));
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).email(EMAIL).status("ACTIVE").build());
        when(authMapper.findSocial("KAKAO", "kakao-user"))
                .thenReturn(UserSocial.builder().userId(2L).provider("KAKAO").providerUserId("kakao-user").build());

        assertThatThrownBy(() -> service.handleOAuthCallback("kakao", "code", "state", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(authMapper, never()).insertSocial(any());
    }

    @Test
    void handleOAuthCallback_unverifiedProviderEmailCannotMergeOrReserveExistingEmail() {
        String temporaryEmail = "kakao_attacker@social.careertuner";
        when(socialOAuthService.isSupported("KAKAO")).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("state", "KAKAO"))
                .thenReturn(new OauthState("oauth_state", "KAKAO", null, "primary"));
        when(socialOAuthService.fetchUserInfo("KAKAO", "code", "state"))
                .thenReturn(new SocialUserInfo("KAKAO", "attacker", EMAIL, "소셜", false));
        when(authMapper.findSocial("KAKAO", "attacker")).thenReturn(null);
        when(userMapper.countByEmail(temporaryEmail)).thenReturn(0);
        org.mockito.Mockito.doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(41L);
            return null;
        }).when(userMapper).insert(any(User.class));
        var jwt = new CareerTunerProperties.Jwt();
        when(props.getJwt()).thenReturn(jwt);
        when(jwtTokenProvider.createAccessToken(41L, temporaryEmail, "USER")).thenReturn("access");
        when(jwtTokenProvider.getAccessValiditySeconds()).thenReturn(1800L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        service.handleOAuthCallback("kakao", "code", "state", null);

        verify(userMapper, never()).findByEmail(EMAIL);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(temporaryEmail);
        assertThat(userCaptor.getValue().isEmailVerified()).isFalse();
        verify(authMapper).insertSocial(org.mockito.ArgumentMatchers.argThat(social ->
                social.getUserId().equals(41L) && "attacker".equals(social.getProviderUserId())));
    }

    @Test
    void handleOAuthCallback_verifiedProviderEmailStillMergesExistingAccount() {
        User existing = User.builder()
                .id(42L).email(EMAIL).name("기존 사용자").status("ACTIVE").role("USER").build();
        when(socialOAuthService.isSupported("GOOGLE")).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("state", "GOOGLE"))
                .thenReturn(new OauthState("oauth_state", "GOOGLE", null, "primary"));
        when(socialOAuthService.fetchUserInfo("GOOGLE", "code", "state"))
                .thenReturn(new SocialUserInfo("GOOGLE", "google-user", EMAIL, "소셜", true));
        when(authMapper.findSocial("GOOGLE", "google-user")).thenReturn(null);
        when(userMapper.findByEmail(EMAIL)).thenReturn(existing);
        var jwt = new CareerTunerProperties.Jwt();
        when(props.getJwt()).thenReturn(jwt);
        when(jwtTokenProvider.createAccessToken(42L, EMAIL, "USER")).thenReturn("access");
        when(jwtTokenProvider.getAccessValiditySeconds()).thenReturn(1800L);

        service.handleOAuthCallback("google", "code", "state", null);

        verify(userMapper, never()).insert(any(User.class));
        verify(authMapper).insertSocial(org.mockito.ArgumentMatchers.argThat(social ->
                social.getUserId().equals(42L) && "google-user".equals(social.getProviderUserId())));
    }

    @Test
    void handleOAuthCallback_verifiedProviderEmailCreatesVerifiedAccountWhenNew() {
        when(socialOAuthService.isSupported("GOOGLE")).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("state", "GOOGLE"))
                .thenReturn(new OauthState("oauth_state", "GOOGLE", null, "primary"));
        when(socialOAuthService.fetchUserInfo("GOOGLE", "code", "state"))
                .thenReturn(new SocialUserInfo("GOOGLE", "new-google-user", EMAIL, "소셜", true));
        when(authMapper.findSocial("GOOGLE", "new-google-user")).thenReturn(null);
        when(userMapper.findByEmail(EMAIL)).thenReturn(null);
        when(userMapper.countByEmail(EMAIL)).thenReturn(0);
        org.mockito.Mockito.doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(43L);
            return null;
        }).when(userMapper).insert(any(User.class));
        var jwt = new CareerTunerProperties.Jwt();
        when(props.getJwt()).thenReturn(jwt);
        when(jwtTokenProvider.createAccessToken(43L, EMAIL, "USER")).thenReturn("access");
        when(jwtTokenProvider.getAccessValiditySeconds()).thenReturn(1800L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        service.handleOAuthCallback("google", "code", "state", null);

        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(userCaptor.getValue().isEmailVerified()).isTrue();
    }

    @Test
    void resolveOAuthFrontendClient_returnsOnlyValueFromValidSignedState() {
        when(socialOAuthService.isSupported("GOOGLE")).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("state", "GOOGLE"))
                .thenReturn(new OauthState("oauth_state", "GOOGLE", null, "sites"));

        assertThat(service.resolveOAuthFrontendClient("google", "state")).isEqualTo("sites");
        assertThat(service.resolveOAuthFrontendClient("unknown", "state")).isNull();
    }

    @Test
    void resolveOAuthCallbackContextMarksOnlySignedLinkStateWithTargetUser() {
        when(socialOAuthService.isSupported("KAKAO")).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("native-link-state", "KAKAO"))
                .thenReturn(new OauthState("oauth_link_state", "KAKAO", 42L, "native"));
        when(jwtTokenProvider.parseOauthState("native-login-state", "KAKAO"))
                .thenReturn(new OauthState(
                        "oauth_native_state", "KAKAO", null, "native", HANDOFF_CHALLENGE));
        when(jwtTokenProvider.parseOauthState("missing-user-link-state", "KAKAO"))
                .thenReturn(new OauthState("oauth_link_state", "KAKAO", null, "native"));
        when(jwtTokenProvider.parseOauthState("tampered-state", "KAKAO")).thenReturn(null);

        OAuthCallbackContext link = service.resolveOAuthCallbackContext("kakao", "native-link-state");
        OAuthCallbackContext login = service.resolveOAuthCallbackContext("kakao", "native-login-state");
        OAuthCallbackContext missingUser = service.resolveOAuthCallbackContext("kakao", "missing-user-link-state");
        OAuthCallbackContext tampered = service.resolveOAuthCallbackContext("kakao", "tampered-state");

        assertThat(link.frontendClient()).isEqualTo("native");
        assertThat(link.socialLink()).isTrue();
        assertThat(login.frontendClient()).isEqualTo("native");
        assertThat(login.socialLink()).isFalse();
        assertThat(missingUser.socialLink()).isFalse();
        assertThat(tampered).isEqualTo(OAuthCallbackContext.invalid());
    }

    @Test
    void oauthProviders_whenMockDisabled_exposesOnlyConfiguredProviders() {
        when(socialOAuthService.isMockEnabled()).thenReturn(false);
        when(socialOAuthService.isConfigured("GOOGLE")).thenReturn(true);
        when(socialOAuthService.isConfigured("KAKAO")).thenReturn(false);
        when(socialOAuthService.isConfigured("NAVER")).thenReturn(true);

        var providers = service.oauthProviders();

        assertThat(providers.google()).isTrue();
        assertThat(providers.kakao()).isFalse();
        assertThat(providers.naver()).isTrue();
    }

    @Test
    void oauthProviders_whenSignedMockEnabled_exposesAllProviders() {
        when(socialOAuthService.isMockEnabled()).thenReturn(true);

        var providers = service.oauthProviders();

        assertThat(providers.google()).isTrue();
        assertThat(providers.kakao()).isTrue();
        assertThat(providers.naver()).isTrue();
    }

    @Test
    void buildSocialLinkUrl_whenProviderIsNotConfigured_returnsMockCallbackUrl() {
        when(socialOAuthService.isSupported("KAKAO")).thenReturn(true);
        when(socialOAuthService.isConfigured("KAKAO")).thenReturn(false);
        when(socialOAuthService.isMockEnabled()).thenReturn(true);
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).email(EMAIL).status("ACTIVE").build());
        when(jwtTokenProvider.createOauthLinkState("KAKAO", 1L, "primary")).thenReturn("signed-state");
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
        when(jwtTokenProvider.createOauthLinkState("KAKAO", 1L, "primary")).thenReturn("signed-state");

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
                .thenReturn(new SocialUserInfo("NAVER", "mock-link-1", null, "네이버 mock 사용자", false));
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

    @Test
    void buildNativeAuthorizationUrl_supportsGoogleBrowserCallbackFlow() {
        when(socialOAuthService.isSupported("GOOGLE")).thenReturn(true);
        when(socialOAuthService.isConfigured("GOOGLE")).thenReturn(true);
        when(jwtTokenProvider.createNativeOauthState("GOOGLE", HANDOFF_CHALLENGE))
                .thenReturn("signed-google-native-state");
        when(socialOAuthService.getAuthorizationUrl("GOOGLE", "signed-google-native-state"))
                .thenReturn("https://accounts.google.com/o/oauth2/v2/auth?state=signed-google-native-state");

        String url = service.buildNativeAuthorizationUrl("google", HANDOFF_CHALLENGE);

        assertThat(url).contains("accounts.google.com").contains("signed-google-native-state");
    }

    @Test
    void buildNativeAuthorizationUrl_localMockKeepsSignedNativeState() {
        when(socialOAuthService.isSupported("NAVER")).thenReturn(true);
        when(socialOAuthService.isConfigured("NAVER")).thenReturn(false);
        when(socialOAuthService.isMockEnabled()).thenReturn(true);
        when(jwtTokenProvider.createNativeOauthState("NAVER", HANDOFF_CHALLENGE))
                .thenReturn("signed-native-state");
        when(socialOAuthService.getMockAuthorizationUrl("NAVER", "signed-native-state"))
                .thenReturn("http://localhost:8080/api/auth/oauth/naver/mock-callback?state=signed-native-state");

        String url = service.buildNativeAuthorizationUrl("naver", HANDOFF_CHALLENGE);

        assertThat(url).contains("/api/auth/oauth/naver/mock-callback?state=signed-native-state");
    }

    @Test
    void nativeMockCallbackCreatesHashedHandoffAndIssuesTokensOnlyAtExchange() {
        when(socialOAuthService.isSupported("NAVER")).thenReturn(true);
        when(socialOAuthService.isMockEnabled()).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("state", "NAVER"))
                .thenReturn(new OauthState(
                        "oauth_native_state", "NAVER", null, "native", HANDOFF_CHALLENGE));
        when(socialOAuthService.mockUserInfo("NAVER", null))
                .thenReturn(new SocialUserInfo("NAVER", "mock-login", EMAIL, "사용자", true));
        org.mockito.Mockito.doAnswer(invocation -> {
            NativeAuthHandoff handoff = invocation.getArgument(0);
            handoff.setId(77L);
            return null;
        }).when(authMapper).insertNativeAuthHandoff(any(NativeAuthHandoff.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return null;
        }).when(userMapper).insert(any(User.class));
        ArgumentCaptor<NativeAuthHandoff> handoffCaptor = ArgumentCaptor.forClass(NativeAuthHandoff.class);

        OAuthCallbackResult callback = service.handleOAuthMockCallback("naver", "state", null);

        assertThat(callback.nativeHandoff()).isTrue();
        assertThat(callback.tokens()).isNull();
        assertThat(callback.handoffCode()).matches("^[A-Za-z0-9_-]{43}$");
        verify(authMapper).insertNativeAuthHandoff(handoffCaptor.capture());
        NativeAuthHandoff stored = handoffCaptor.getValue();
        assertThat(stored.getCodeHash()).isEqualTo(sha256Base64Url(callback.handoffCode()));
        assertThat(stored.getCodeHash()).isNotEqualTo(callback.handoffCode());
        assertThat(stored.getProvider()).isEqualTo("NAVER");
        assertThat(stored.getProviderUserId()).isEqualTo("mock-login");
        assertThat(stored.getEmail()).isEqualTo(EMAIL);
        assertThat(stored.isEmailVerified()).isTrue();
        assertThat(stored.getDisplayName()).isEqualTo("사용자");
        assertThat(stored.getHandoffChallenge()).isEqualTo(HANDOFF_CHALLENGE);
        verify(authMapper, never()).insertRefreshToken(any());
        verify(authMapper, never()).findSocial(any(), any());
        verify(authMapper, never()).insertSocial(any());
        verify(userMapper, never()).insert(any(User.class));
        verify(userMapper, never()).touchLastLoginAndResetFailures(anyLong());

        when(authMapper.findNativeAuthHandoffForUpdate(stored.getCodeHash())).thenReturn(stored);
        when(authMapper.consumeNativeAuthHandoff(77L)).thenReturn(1);
        when(authMapper.findSocial("NAVER", "mock-login")).thenReturn(null);
        when(userMapper.findByEmail(EMAIL)).thenReturn(null);
        when(userMapper.countByEmail(EMAIL)).thenReturn(0);
        var jwt = new CareerTunerProperties.Jwt();
        when(props.getJwt()).thenReturn(jwt);
        when(jwtTokenProvider.createAccessToken(1L, EMAIL, "USER")).thenReturn("native-access");
        when(jwtTokenProvider.getAccessValiditySeconds()).thenReturn(1800L);

        TokenResponse tokens = service.exchangeNativeOAuthHandoff(
                callback.handoffCode(), HANDOFF_VERIFIER, null);

        assertThat(tokens.accessToken()).isEqualTo("native-access");
        var order = org.mockito.Mockito.inOrder(authMapper, userMapper);
        order.verify(authMapper).consumeNativeAuthHandoff(77L);
        order.verify(userMapper).insert(any(User.class));
        order.verify(authMapper).insertSocial(org.mockito.ArgumentMatchers.argThat(social ->
                social.getUserId().equals(1L)
                        && "NAVER".equals(social.getProvider())
                        && "mock-login".equals(social.getProviderUserId())));
        order.verify(authMapper).insertRefreshToken(any());
        verify(userMapper).findByEmail(EMAIL);
        verify(userMapper).touchLastLoginAndResetFailures(1L);
    }

    @Test
    void nativeProviderCallbackStagesIdentityWithoutCreatingAccount() {
        when(socialOAuthService.isSupported("GOOGLE")).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("state", "GOOGLE"))
                .thenReturn(new OauthState(
                        "oauth_native_state", "GOOGLE", null, "native", HANDOFF_CHALLENGE));
        when(socialOAuthService.fetchUserInfo("GOOGLE", "code", "state"))
                .thenReturn(new SocialUserInfo("GOOGLE", "google-user", null, null, false));
        ArgumentCaptor<NativeAuthHandoff> handoffCaptor = ArgumentCaptor.forClass(NativeAuthHandoff.class);

        OAuthCallbackResult callback = service.handleOAuthCallback("google", "code", "state", null);

        assertThat(callback.nativeHandoff()).isTrue();
        verify(authMapper).insertNativeAuthHandoff(handoffCaptor.capture());
        assertThat(handoffCaptor.getValue().getProvider()).isEqualTo("GOOGLE");
        assertThat(handoffCaptor.getValue().getProviderUserId()).isEqualTo("google-user");
        assertThat(handoffCaptor.getValue().getEmail()).isNull();
        assertThat(handoffCaptor.getValue().getDisplayName()).isNull();
        verify(authMapper, never()).findSocial(any(), any());
        verify(authMapper, never()).insertSocial(any());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void nativeCallbackRejectsOversizedProviderPiiBeforePersistence() {
        when(socialOAuthService.isSupported("NAVER")).thenReturn(true);
        when(socialOAuthService.isMockEnabled()).thenReturn(true);
        when(jwtTokenProvider.parseOauthState("state", "NAVER"))
                .thenReturn(new OauthState(
                        "oauth_native_state", "NAVER", null, "native", HANDOFF_CHALLENGE));
        when(socialOAuthService.mockUserInfo("NAVER", null))
                .thenReturn(new SocialUserInfo("NAVER", "mock-login", EMAIL, "이".repeat(101), true));

        assertThatThrownBy(() -> service.handleOAuthMockCallback("naver", "state", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(authMapper, never()).insertNativeAuthHandoff(any());
        verify(authMapper, never()).findSocial(any(), any());
        verify(authMapper, never()).insertSocial(any());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void exchangeNativeOAuthHandoff_wrongVerifierDoesNotConsumeOrIssueTokens() {
        String handoffCode = "c".repeat(43);
        when(authMapper.findNativeAuthHandoffForUpdate(sha256Base64Url(handoffCode)))
                .thenReturn(validHandoff(HANDOFF_CHALLENGE));

        assertThatThrownBy(() -> service.exchangeNativeOAuthHandoff(
                handoffCode, "w".repeat(43), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(authMapper, never()).consumeNativeAuthHandoff(any());
        verify(authMapper, never()).insertRefreshToken(any());
        verify(authMapper, never()).findSocial(any(), any());
        verify(authMapper, never()).insertSocial(any());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void exchangeNativeOAuthHandoff_unverifiedEmailCannotMergeAfterNativeBoundary() {
        String handoffCode = "u".repeat(43);
        String temporaryEmail = "naver_naver-user@social.careertuner";
        NativeAuthHandoff handoff = validHandoff(HANDOFF_CHALLENGE);
        handoff.setEmailVerified(false);
        when(authMapper.findNativeAuthHandoffForUpdate(sha256Base64Url(handoffCode))).thenReturn(handoff);
        when(authMapper.consumeNativeAuthHandoff(77L)).thenReturn(1);
        when(authMapper.findSocial("NAVER", "naver-user")).thenReturn(null);
        when(userMapper.countByEmail(temporaryEmail)).thenReturn(0);
        org.mockito.Mockito.doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(44L);
            return null;
        }).when(userMapper).insert(any(User.class));
        var jwt = new CareerTunerProperties.Jwt();
        when(props.getJwt()).thenReturn(jwt);
        when(jwtTokenProvider.createAccessToken(44L, temporaryEmail, "USER")).thenReturn("access");
        when(jwtTokenProvider.getAccessValiditySeconds()).thenReturn(1800L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        service.exchangeNativeOAuthHandoff(handoffCode, HANDOFF_VERIFIER, null);

        verify(userMapper, never()).findByEmail(EMAIL);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(temporaryEmail);
        assertThat(userCaptor.getValue().isEmailVerified()).isFalse();
    }

    @Test
    void exchangeNativeOAuthHandoff_expiredOrReplayedCodeIsRejected() {
        String expiredCode = "e".repeat(43);
        NativeAuthHandoff expired = validHandoff(HANDOFF_CHALLENGE);
        when(authMapper.findNativeAuthHandoffForUpdate(sha256Base64Url(expiredCode))).thenReturn(expired);
        when(authMapper.consumeNativeAuthHandoff(77L)).thenReturn(0);

        assertThatThrownBy(() -> service.exchangeNativeOAuthHandoff(
                expiredCode, HANDOFF_VERIFIER, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);

        String replayedCode = "r".repeat(43);
        when(authMapper.findNativeAuthHandoffForUpdate(sha256Base64Url(replayedCode))).thenReturn(null);

        assertThatThrownBy(() -> service.exchangeNativeOAuthHandoff(
                replayedCode, HANDOFF_VERIFIER, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(authMapper).consumeNativeAuthHandoff(77L);
        verify(authMapper, never()).insertRefreshToken(any());
    }

    @Test
    void exchangeNativeOAuthHandoff_lostConcurrentConsumeRaceIsRejected() {
        String handoffCode = "q".repeat(43);
        NativeAuthHandoff handoff = validHandoff(HANDOFF_CHALLENGE);
        when(authMapper.findNativeAuthHandoffForUpdate(sha256Base64Url(handoffCode))).thenReturn(handoff);
        when(authMapper.consumeNativeAuthHandoff(77L)).thenReturn(0);

        assertThatThrownBy(() -> service.exchangeNativeOAuthHandoff(
                handoffCode, HANDOFF_VERIFIER, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(authMapper, never()).insertRefreshToken(any());
    }

    @Test
    void logoutAllByRefreshTokenRevokesEverySessionWithoutAccessToken() {
        RefreshToken token = RefreshToken.builder()
                .userId(42L)
                .token("refresh-all")
                .expiredAt(LocalDateTime.now().plusHours(1))
                .revoked(false)
                .build();
        when(authMapper.findRefreshToken("refresh-all")).thenReturn(token);

        service.logoutAllByRefreshToken("refresh-all", null);

        verify(authMapper).revokeAllForUser(42L);
        verify(pushSubscriptionMapper).deleteAllByUserId(42L);
        verify(authMapper).insertLoginHistory(org.mockito.ArgumentMatchers.argThat(history ->
                history.getUserId().equals(42L)
                        && history.getEventType().equals("LOGOUT_ALL")
                        && history.isSuccess()));
    }

    @Test
    void logoutAllByRefreshTokenRejectsAlreadyRevokedToken() {
        RefreshToken token = RefreshToken.builder()
                .userId(42L)
                .token("revoked")
                .expiredAt(LocalDateTime.now().plusHours(1))
                .revoked(true)
                .build();
        when(authMapper.findRefreshToken("revoked")).thenReturn(token);

        service.logoutAllByRefreshToken("revoked", null);

        verify(authMapper, never()).revokeAllForUser(anyLong());
    }

    private static NativeAuthHandoff validHandoff(String challenge) {
        return NativeAuthHandoff.builder()
                .id(77L)
                .provider("NAVER")
                .providerUserId("naver-user")
                .email(EMAIL)
                .emailVerified(true)
                .displayName("사용자")
                .handoffChallenge(challenge)
                .build();
    }

    private static String sha256Base64Url(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
