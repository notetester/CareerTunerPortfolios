package com.careertuner.auth.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.auth.domain.EmailVerification;
import com.careertuner.auth.domain.MfaChallenge;
import com.careertuner.auth.domain.RefreshToken;
import com.careertuner.auth.domain.UserConsent;
import com.careertuner.auth.domain.UserLoginHistory;
import com.careertuner.auth.domain.UserSocial;
import com.careertuner.auth.dto.LoginRequest;
import com.careertuner.auth.dto.LoginRequestContext;
import com.careertuner.auth.dto.LoginResponse;
import com.careertuner.auth.dto.MeResponse;
import com.careertuner.auth.dto.MfaLoginStatusResponse;
import com.careertuner.auth.dto.MfaLoginVerifyRequest;
import com.careertuner.auth.dto.OAuthCallbackResult;
import com.careertuner.auth.dto.PasswordResetConfirmRequest;
import com.careertuner.auth.dto.PasswordResetRequest;
import com.careertuner.auth.dto.RegisterRequest;
import com.careertuner.auth.dto.TokenRequest;
import com.careertuner.auth.dto.TokenResponse;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.activitylog.domain.UserSecurityHistory;
import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.JwtTokenProvider;
import com.careertuner.common.security.JwtTokenProvider.OauthState;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.reward.service.RewardService;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DORMANT = "DORMANT";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String STATUS_DELETED = "DELETED";

    private final UserMapper userMapper;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final SocialOAuthService socialOAuthService;
    private final MfaService mfaService;
    private final CareerTunerProperties props;
    private final com.careertuner.activitylog.service.SecurityHistoryService securityHistoryService;
    /**
     * 로그인 실패 자동 잠금 정책(관리자 편집). OFF 면 무제약, ON 이면 정책값으로 잠근다.
     * 기본값은 기존 상수(5회/10분)와 동일 — 도입 시 동작 무변경.
     */
    private final com.careertuner.loginrisk.service.LoginRiskPolicyService loginRiskPolicyService;
    /** 활동 리워드 적립(하루 첫 로그인 시 DAILY_LOGIN, 일일 캡 1회). 규칙 off 면 미적립. */
    private final RewardService rewardService;

    /** 리워드 적립은 로그인 처리 실패로 이어지지 않도록 예외를 흡수한다. */
    private void grantDailyLoginRewardSafely(Long userId) {
        try {
            rewardService.grant(userId, "DAILY_LOGIN", "LOGIN", null);
        } catch (RuntimeException e) {
            log.warn("일일 로그인 리워드 적립 실패 userId={} : {}", userId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request, LoginRequestContext context) {
        String email = normalizeOptionalEmail(request.email());
        if (email != null && userMapper.countByEmail(email) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다.");
        }
        String loginId = normalizeLoginId(request.loginId());
        if (userMapper.countByLoginId(loginId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
        }
        requireSignupConsents(request);

        User user = User.builder()
                .email(email)
                .loginId(loginId)
                .password(passwordEncoder.encode(request.password()))
                .passwordEnabled(true)
                .name(request.name().trim())
                .emailVerified(false)
                .userType("JOB_SEEKER")
                .role("USER")
                .status(STATUS_ACTIVE)
                .plan("FREE")
                .credit(0)
                .build();
        userMapper.insert(user);
        recordSignupConsents(user.getId(), request);

        if (email != null) {
            issueEmailVerification(user);
        }
        // 회원가입 직후 자동 로그인 정책이므로 로그인 성공과 동일하게 접속 정보를 남긴다.
        userMapper.touchLastLoginAndResetFailures(user.getId());
        recordLoginHistory(user.getId(), "LOGIN", "LOCAL", "LOGIN_ID", loginId, true, null, context);
        grantDailyLoginRewardSafely(user.getId());
        return issueTokens(user, context);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request, LoginRequestContext context) {
        String identifier = normalizeLoginIdentifier(request.email());
        String loginMethod = loginMethodFor(identifier);
        User user = userMapper.findByLoginIdentifier(identifier);
        if (user == null) {
            recordLoginHistory(null, "LOGIN", "LOCAL", loginMethod, identifier, false, "USER_NOT_FOUND", context);
            throw invalidLogin();
        }

        user = releaseExpiredBlockIfNeeded(user);
        // 차단/휴면/삭제 계정에는 토큰을 발급하지 않기 위해 비밀번호 검증 전에 상태를 먼저 본다.
        validateLoginAllowed(user, loginMethod, identifier, context);

        if (!user.isPasswordEnabled() || user.getPassword() == null) {
            recordLoginHistory(user.getId(), "LOGIN", "LOCAL", loginMethod, identifier, false,
                    "PASSWORD_LOGIN_DISABLED", context);
            throw invalidLogin();
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            userMapper.increaseFailedLogin(user.getId());
            recordLoginHistory(user.getId(), "LOGIN", "LOCAL", loginMethod, identifier, false, "WRONG_PASSWORD", context);
            // 자동 잠금 정책 — OFF 면 무제약(집계만), ON 이면 임계 초과 시 잠금.
            if (loginRiskPolicyService.isLockoutEnabled()) {
                int maxCount = loginRiskPolicyService.getMaxFailedCount();
                if (user.getFailedLoginCount() + 1 >= maxCount) {
                    LocalDateTime blockedUntil = LocalDateTime.now()
                            .plusMinutes(loginRiskPolicyService.getLockMinutes());
                    String reason = "로그인 실패 " + maxCount + "회 초과";
                    userMapper.lockForFailedLogin(user.getId(), blockedUntil, reason);
                    authMapper.revokeAllForUser(user.getId());
                    authMapper.insertUserStatusHistory(user.getId(), null, user.getStatus(), STATUS_BLOCKED,
                            reason, "자동 계정 잠금", blockedUntil);
                }
            }
            throw invalidLogin();
        }

        LoginResponse mfaChallenge = mfaService.beginLoginIfRequired(user, context);
        if (mfaChallenge != null) {
            recordLoginHistory(user.getId(), "LOGIN_MFA_REQUIRED", "LOCAL", loginMethod, identifier, true, null, context);
            return mfaChallenge;
        }

        userMapper.touchLastLoginAndResetFailures(user.getId());
        recordLoginHistory(user.getId(), "LOGIN", "LOCAL", loginMethod, identifier, true, null, context);
        // 하루 첫 로그인 리워드(일일 캡 1회, 규칙 on 일 때만). 실패해도 로그인은 정상 처리.
        grantDailyLoginRewardSafely(user.getId());
        return LoginResponse.authenticated(issueTokens(user, context));
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse verifyMfaLogin(MfaLoginVerifyRequest request, LoginRequestContext context) {
        User user = mfaService.verifyLoginChallenge(
                request.challengeToken(),
                request.code(),
                request.backupCode(),
                Boolean.TRUE.equals(request.useApprovedChallenge())
        );
        userMapper.touchLastLoginAndResetFailures(user.getId());
        recordLoginHistory(user.getId(), "LOGIN", "LOCAL", "MFA", user.getEmail(), true, null, context);
        grantDailyLoginRewardSafely(user.getId());
        return LoginResponse.authenticated(issueTokens(user, context));
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public MfaLoginStatusResponse mfaLoginStatus(String challengeToken, LoginRequestContext context) {
        MfaChallenge challenge = mfaService.findChallenge(challengeToken);
        if (challenge == null) {
            return new MfaLoginStatusResponse("NOT_FOUND", null);
        }
        if (challenge.getExpiresAt() != null && challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            return new MfaLoginStatusResponse("EXPIRED", null);
        }
        if ("APPROVED".equals(challenge.getStatus())) {
            LoginResponse completed = verifyMfaLogin(
                    new MfaLoginVerifyRequest(challengeToken, null, null, true),
                    context
            );
            return new MfaLoginStatusResponse("VERIFIED", completed.token());
        }
        return new MfaLoginStatusResponse(challenge.getStatus(), null);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String refreshToken, LoginRequestContext context) {
        RefreshToken stored = authMapper.findRefreshToken(refreshToken);
        if (stored == null || stored.isRevoked() || stored.getExpiredAt().isBefore(LocalDateTime.now())) {
            recordLoginHistory(stored != null ? stored.getUserId() : null, "REFRESH", "LOCAL",
                    "REFRESH_TOKEN", null, false, "INVALID_REFRESH_TOKEN", context);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다.");
        }

        User user = userMapper.findById(stored.getUserId());
        if (user == null) {
            recordLoginHistory(stored.getUserId(), "REFRESH", "LOCAL", "REFRESH_TOKEN", null, false,
                    "USER_NOT_FOUND", context);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "사용자를 찾을 수 없습니다.");
        }

        user = releaseExpiredBlockIfNeeded(user);
        // refresh token이 유효해도 현재 계정 상태가 비활성화되었다면 세션을 연장하지 않는다.
        validateTokenAllowed(user, context);

        authMapper.revokeRefreshToken(refreshToken);
        recordLoginHistory(user.getId(), "REFRESH", "LOCAL", "REFRESH_TOKEN", user.getEmail(), true, null, context);
        return issueTokens(user, context);
    }

    @Override
    @Transactional
    public void logout(String refreshToken, LoginRequestContext context) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        RefreshToken stored = authMapper.findRefreshToken(refreshToken);
        authMapper.revokeRefreshToken(refreshToken);
        if (stored != null) {
            User user = userMapper.findById(stored.getUserId());
            recordLoginHistory(stored.getUserId(), "LOGOUT", "LOCAL", "REFRESH_TOKEN",
                    user != null ? user.getEmail() : null, true, null, context);
        }
    }

    @Override
    @Transactional
    public void logoutAll(Long userId) {
        authMapper.revokeAllForUser(userId);
    }

    @Override
    @Transactional
    public boolean verifyEmail(String token) {
        EmailVerification ev = authMapper.findEmailVerificationByToken(token);
        if (ev == null || ev.isUsed()
                || ev.getExpiredAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        if ("VERIFY".equals(ev.getPurpose())) {
            authMapper.markEmailVerificationUsed(ev.getId());
            if (ev.getUserId() != null) {
                userMapper.markEmailVerified(ev.getUserId());
            }
            securityHistoryService.record("EMAIL_VERIFY", "COMPLETE", ev.getUserId(), true, null, null);
            return true;
        }

        if ("EMAIL_CHANGE".equals(ev.getPurpose())) {
            User user = ev.getUserId() != null ? userMapper.findById(ev.getUserId()) : null;
            if (user == null || STATUS_DELETED.equals(user.getStatus())) {
                return false;
            }
            String email = normalizeEmail(ev.getEmail());
            if (userMapper.countByEmailExcludingId(email, user.getId()) > 0) {
                return false;
            }
            authMapper.markEmailVerificationUsed(ev.getId());
            userMapper.updateEmailAndMarkVerified(user.getId(), email);
            securityHistoryService.record("EMAIL_CHANGE", "COMPLETE", user.getId(), true, email, null);
            return true;
        }

        return false;
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        User user = userMapper.findByEmail(normalizeEmail(email));
        if (user == null || user.isEmailVerified()) {
            return;
        }
        issueEmailVerification(user);
    }

    @Override
    @Transactional
    public void requestFindId(String email, LoginRequestContext context) {
        String normalizedEmail = normalizeEmail(email);
        User user = userMapper.findByEmail(normalizedEmail);
        recordSecurityEvent(user != null ? user.getId() : null, null, "FIND_ID", "REQUEST",
                normalizedEmail, normalizedEmail, true, null, null, context);

        if (user == null) {
            recordSecurityEvent(null, null, "FIND_ID", "ISSUE", normalizedEmail, normalizedEmail,
                    false, "EMAIL_NOT_FOUND", null, context);
            return;
        }
        if (!user.isEmailVerified()) {
            recordSecurityEvent(user.getId(), null, "FIND_ID", "ISSUE", normalizedEmail, normalizedEmail,
                    false, "EMAIL_NOT_VERIFIED", null, context);
            return;
        }
        if (user.getLoginId() == null || user.getLoginId().isBlank()) {
            recordSecurityEvent(user.getId(), null, "FIND_ID", "ISSUE", normalizedEmail, normalizedEmail,
                    false, "LOGIN_ID_NOT_SET", null, context);
            return;
        }
        if (!isRecoverableAccountStatus(user.getStatus())) {
            recordSecurityEvent(user.getId(), null, "FIND_ID", "ISSUE", normalizedEmail, normalizedEmail,
                    false, "ACCOUNT_NOT_RECOVERABLE", null, context);
            return;
        }

        authMapper.expireUnusedEmailVerifications(user.getEmail(), "FIND_ID");
        EmailVerification verification = issueEmailVerification(user, "FIND_ID", LocalDateTime.now().plusMinutes(30));
        emailService.sendFindIdEmail(user.getEmail(), verification.getToken());
        recordSecurityEvent(user.getId(), null, "FIND_ID", "ISSUE", normalizedEmail, user.getEmail(),
                true, null, null, context);
    }

    @Override
    @Transactional
    public String verifyFindId(String token, LoginRequestContext context) {
        EmailVerification ev = authMapper.findEmailVerificationByToken(token);
        if (ev == null || ev.isUsed() || !"FIND_ID".equals(ev.getPurpose())
                || ev.getExpiredAt().isBefore(LocalDateTime.now())) {
            recordSecurityEvent(null, null, "FIND_ID", "VERIFY", null, null,
                    false, "TOKEN_INVALID_OR_EXPIRED", null, context);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "아이디 확인 링크가 만료되었거나 유효하지 않습니다.");
        }

        User user = ev.getUserId() != null ? userMapper.findById(ev.getUserId()) : userMapper.findByEmail(ev.getEmail());
        authMapper.markEmailVerificationUsed(ev.getId());
        if (user == null || !user.isEmailVerified() || user.getLoginId() == null || user.getLoginId().isBlank()
                || !isRecoverableAccountStatus(user.getStatus())) {
            recordSecurityEvent(user != null ? user.getId() : ev.getUserId(), null, "FIND_ID", "VERIFY",
                    ev.getEmail(), ev.getEmail(), false, "LOGIN_ID_NOT_AVAILABLE", null, context);
            throw new BusinessException(ErrorCode.NOT_FOUND, "확인 가능한 로그인 아이디가 없습니다.");
        }

        recordSecurityEvent(user.getId(), null, "FIND_ID", "VERIFY", ev.getEmail(), ev.getEmail(),
                true, null, null, context);
        recordSecurityEvent(user.getId(), null, "FIND_ID", "COMPLETE", ev.getEmail(), ev.getEmail(),
                true, null, null, context);
        return maskLoginId(user.getLoginId());
    }

    @Override
    @Transactional
    public void requestPasswordReset(PasswordResetRequest request, LoginRequestContext context) {
        String identifier = normalizeLoginIdentifier(request.loginIdentifier());
        if (identifier == null || identifier.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아이디 또는 이메일을 입력해 주세요.");
        }
        User user = userMapper.findByLoginIdentifier(identifier);
        String method = loginMethodFor(identifier);
        recordSecurityEvent(user != null ? user.getId() : null, null, "FIND_PASSWORD", "REQUEST",
                identifier, user != null ? user.getEmail() : null, true, null, null, context);
        if (user == null) {
            recordLoginHistory(null, "PASSWORD_RESET", "LOCAL", method, identifier, false, "USER_NOT_FOUND", context);
            recordSecurityEvent(null, null, "FIND_PASSWORD", "ISSUE", identifier, null,
                    false, "USER_NOT_FOUND", null, context);
            return;
        }
        if (!isRecoverableAccountStatus(user.getStatus())) {
            recordLoginHistory(user.getId(), "PASSWORD_RESET", "LOCAL", method, identifier, false,
                    "ACCOUNT_NOT_RECOVERABLE", context);
            recordSecurityEvent(user.getId(), null, "FIND_PASSWORD", "ISSUE", identifier, user.getEmail(),
                    false, "ACCOUNT_NOT_RECOVERABLE", null, context);
            return;
        }
        if (isTemporaryEmail(user.getEmail()) || !user.isEmailVerified()) {
            recordLoginHistory(user.getId(), "PASSWORD_RESET", "LOCAL", method, identifier, false,
                    "EMAIL_NOT_VERIFIED", context);
            recordSecurityEvent(user.getId(), null, "FIND_PASSWORD", "ISSUE", identifier, user.getEmail(),
                    false, "EMAIL_NOT_VERIFIED", null, context);
            return;
        }
        recordLoginHistory(user.getId(), "PASSWORD_RESET", "LOCAL", method, identifier, true, null, context);
        authMapper.expireUnusedEmailVerifications(user.getEmail(), "RESET_PW");
        EmailVerification verification = issueEmailVerification(user, "RESET_PW", 1);
        emailService.sendPasswordResetEmail(user.getEmail(), verification.getToken());
        recordSecurityEvent(user.getId(), null, "FIND_PASSWORD", "ISSUE", identifier, user.getEmail(),
                true, null, null, context);
        securityHistoryService.record("RESET_PASSWORD", "REQUEST", user.getId(), true, identifier, null);
    }

    @Override
    @Transactional
    public void resetPassword(PasswordResetConfirmRequest request, LoginRequestContext context) {
        EmailVerification ev = authMapper.findEmailVerificationByToken(request.token());
        if (ev == null || ev.isUsed() || !"RESET_PW".equals(ev.getPurpose())
                || ev.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 비밀번호 재설정 토큰입니다.");
        }
        User user = userMapper.findById(ev.getUserId());
        if (user == null || STATUS_DELETED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "사용할 수 없는 계정입니다.");
        }
        authMapper.markEmailVerificationUsed(ev.getId());
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(request.newPassword()));
        authMapper.revokeAllForUser(user.getId());
        recordLoginHistory(user.getId(), "PASSWORD_RESET", "LOCAL", "EMAIL", user.getEmail(), true, null, context);
        securityHistoryService.record("RESET_PASSWORD", "COMPLETE", user.getId(), true, user.getEmail(), null);
    }

    @Override
    @Transactional
    public void requestDormantRelease(PasswordResetRequest request, LoginRequestContext context) {
        String email = normalizeEmail(request.loginIdentifier());
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일을 입력해 주세요.");
        }
        User user = userMapper.findByEmail(email);
        recordLoginHistory(user != null ? user.getId() : null, "DORMANT_RELEASE", "LOCAL", "EMAIL",
                email, true, null, context);
        if (user == null || !STATUS_DORMANT.equals(user.getStatus())) {
            return;
        }
        EmailVerification verification = issueEmailVerification(user, "DORMANT_RELEASE", 1);
        emailService.sendDormantReleaseEmail(user.getEmail(), verification.getToken());
    }

    @Override
    @Transactional
    public TokenResponse releaseDormant(TokenRequest request, LoginRequestContext context) {
        EmailVerification ev = authMapper.findEmailVerificationByToken(request.token());
        if (ev == null || ev.isUsed() || !"DORMANT_RELEASE".equals(ev.getPurpose())
                || ev.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 휴면 해제 토큰입니다.");
        }
        User user = userMapper.findById(ev.getUserId());
        if (user == null || !STATUS_DORMANT.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "휴면 해제 대상 계정이 아닙니다.");
        }
        authMapper.markEmailVerificationUsed(ev.getId());
        userMapper.releaseDormant(user.getId());
        authMapper.insertUserStatusHistory(user.getId(), user.getId(), STATUS_DORMANT, STATUS_ACTIVE,
                "휴면 해제 인증 완료", "사용자 휴면 해제", null);
        User activeUser = userMapper.findById(user.getId());
        userMapper.touchLastLoginAndResetFailures(activeUser.getId());
        recordLoginHistory(activeUser.getId(), "LOGIN", "LOCAL", "EMAIL", activeUser.getEmail(), true, null, context);
        return issueTokens(activeUser, context);
    }

    @Override
    public boolean isEmailTaken(String email) {
        return userMapper.countByEmail(normalizeEmail(email)) > 0;
    }

    @Override
    public boolean isLoginIdTaken(String loginId) {
        String normalized = normalizeOptionalLoginId(loginId);
        return normalized != null && userMapper.countByLoginId(normalized) > 0;
    }

    @Override
    public MeResponse me(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return buildMeResponse(user);
    }

    @Override
    public String buildAuthorizationUrl(String provider) {
        String normalized = normalizeProvider(provider);
        String state = jwtTokenProvider.createOauthState(normalized);
        if (!socialOAuthService.isConfigured(normalized)) {
            return buildMockAuthorizationUrl(normalized, state);
        }
        return socialOAuthService.getAuthorizationUrl(normalized, state);
    }

    @Override
    public String buildSocialLinkUrl(Long userId, String provider) {
        String normalized = normalizeProvider(provider);
        if (userMapper.findById(userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        String state = jwtTokenProvider.createOauthLinkState(normalized, userId);
        if (!socialOAuthService.isConfigured(normalized)) {
            return buildMockAuthorizationUrl(normalized, state);
        }
        return socialOAuthService.getAuthorizationUrl(normalized, state);
    }

    @Override
    @Transactional
    public OAuthCallbackResult handleOAuthCallback(String provider, String code, String state, LoginRequestContext context) {
        String normalized = normalizeProvider(provider);
        OauthState oauthState = jwtTokenProvider.parseOauthState(state, normalized);
        if (oauthState == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "잘못된 OAuth state입니다.");
        }

        SocialUserInfo info = socialOAuthService.fetchUserInfo(normalized, code, state);
        if (oauthState.link()) {
            if (oauthState.userId() == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "잘못된 OAuth 연동 state입니다.");
            }
            linkSocial(oauthState.userId(), info, context);
            return OAuthCallbackResult.linked(info.provider());
        }

        User user = findOrCreateSocialUser(info);
        user = releaseExpiredBlockIfNeeded(user);
        validateSocialLoginAllowed(user, info, context);

        userMapper.touchLastLoginAndResetFailures(user.getId());
        String identifier = info.email() != null ? info.email() : info.providerUserId();
        recordLoginHistory(user.getId(), "LOGIN", info.provider(), "OAUTH", identifier, true, null, context);
        return OAuthCallbackResult.login(issueTokens(user, context));
    }

    @Override
    @Transactional
    public OAuthCallbackResult handleOAuthMockCallback(String provider, String state, LoginRequestContext context) {
        String normalized = normalizeProvider(provider);
        if (!socialOAuthService.isMockEnabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "OAuth mock 콜백이 비활성화되어 있습니다.");
        }
        OauthState oauthState = jwtTokenProvider.parseOauthState(state, normalized);
        if (oauthState == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "잘못된 OAuth state입니다.");
        }
        if (oauthState.link()) {
            if (oauthState.userId() == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "잘못된 OAuth 연동 state입니다.");
            }
            SocialUserInfo info = socialOAuthService.mockUserInfo(normalized, oauthState.userId());
            linkSocial(oauthState.userId(), info, context);
            return OAuthCallbackResult.linked(info.provider());
        }

        SocialUserInfo info = socialOAuthService.mockUserInfo(normalized, null);
        User user = findOrCreateSocialUser(info);
        user = releaseExpiredBlockIfNeeded(user);
        validateSocialLoginAllowed(user, info, context);
        userMapper.touchLastLoginAndResetFailures(user.getId());
        recordLoginHistory(user.getId(), "LOGIN", info.provider(), "OAUTH", info.providerUserId(),
                true, null, context);
        recordSecurityEvent(user.getId(), null, "SOCIAL_LOGIN", "MOCK_COMPLETE",
                info.providerUserId(), user.getEmail(), true, null, info.provider(), context);
        return OAuthCallbackResult.login(issueTokens(user, context));
    }

    private String buildMockAuthorizationUrl(String provider, String state) {
        if (!socialOAuthService.isMockEnabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    provider + " OAuth 설정이 없어 현재 소셜 인증을 사용할 수 없습니다.");
        }
        return socialOAuthService.getMockAuthorizationUrl(provider, state);
    }

    private User findOrCreateSocialUser(SocialUserInfo info) {
        UserSocial social = authMapper.findSocial(info.provider(), info.providerUserId());
        if (social != null) {
            User user = userMapper.findById(social.getUserId());
            if (user == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "연결된 소셜 계정을 찾을 수 없습니다.");
            }
            return user;
        }

        User user = null;
        if (info.email() != null && !info.email().isBlank()) {
            user = userMapper.findByEmail(normalizeEmail(info.email()));
            if (user != null && STATUS_DELETED.equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "탈퇴 또는 삭제된 계정입니다.");
            }
        }
        if (user == null) {
            String email = info.email() != null && !info.email().isBlank()
                    ? normalizeEmail(info.email())
                    : info.provider().toLowerCase(Locale.ROOT) + "_" + info.providerUserId() + "@social.careertuner";
            if (userMapper.countByEmail(email) > 0) {
                email = info.provider().toLowerCase(Locale.ROOT) + "_" + info.providerUserId()
                        + "_" + System.currentTimeMillis() + "@social.careertuner";
            }
            boolean providerEmailPresent = info.email() != null && !info.email().isBlank();
            user = User.builder()
                    .email(email)
                    .password(null)
                    .passwordEnabled(false)
                    .name(info.name() != null && !info.name().isBlank() ? info.name() : info.provider() + " 사용자")
                    .emailVerified(providerEmailPresent)
                    .userType("JOB_SEEKER")
                    .role("USER")
                    .status(STATUS_ACTIVE)
                    .plan("FREE")
                    .credit(0)
                    .build();
            userMapper.insert(user);
        }

        // 소셜 제공자가 이메일을 내려주고 같은 이메일의 기존 계정이 있으면 자동으로 연결한다.
        // 단, 삭제 계정은 위에서 차단하고, 차단/휴면 계정은 연결 후 validateSocialLoginAllowed에서 로그인만 막는다.
        authMapper.insertSocial(UserSocial.builder()
                .userId(user.getId())
                .provider(info.provider())
                .providerUserId(info.providerUserId())
                .build());
        return user;
    }

    private void linkSocial(Long userId, SocialUserInfo info, LoginRequestContext context) {
        User user = userMapper.findById(userId);
        if (user == null || STATUS_DELETED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "소셜 계정을 연결할 수 없는 회원입니다.");
        }
        UserSocial linkedToAnyUser = authMapper.findSocial(info.provider(), info.providerUserId());
        if (linkedToAnyUser != null && !linkedToAnyUser.getUserId().equals(userId)) {
            recordSecurityEvent(userId, userId, "SOCIAL_LINK", "COMPLETE",
                    info.providerUserId(), user.getEmail(), false, "SOCIAL_ALREADY_LINKED", info.provider(), context);
            throw new BusinessException(ErrorCode.CONFLICT, "이미 다른 계정에 연결된 소셜 계정입니다.");
        }
        if (linkedToAnyUser != null) {
            return;
        }
        if (authMapper.findSocialByUserAndProvider(userId, info.provider()) != null) {
            recordSecurityEvent(userId, userId, "SOCIAL_LINK", "COMPLETE",
                    info.providerUserId(), user.getEmail(), false, "PROVIDER_ALREADY_LINKED", info.provider(), context);
            throw new BusinessException(ErrorCode.CONFLICT, "이미 연결된 소셜 제공자입니다.");
        }
        try {
            authMapper.insertSocial(UserSocial.builder()
                    .userId(userId)
                    .provider(info.provider())
                    .providerUserId(info.providerUserId())
                    .build());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 연결된 소셜 계정입니다.");
        }
        recordSecurityEvent(userId, userId, "SOCIAL_LINK", "COMPLETE",
                info.providerUserId(), user.getEmail(), true, null, info.provider(), context);
    }

    private void issueEmailVerification(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일이 등록되지 않은 계정입니다.");
        }
        EmailVerification verification = issueEmailVerification(user, "VERIFY", 24);
        emailService.sendVerificationEmail(user.getEmail(), verification.getToken());
    }

    private EmailVerification issueEmailVerification(User user, String purpose, int validHours) {
        return issueEmailVerification(user, purpose, LocalDateTime.now().plusHours(validHours));
    }

    private EmailVerification issueEmailVerification(User user, String purpose, LocalDateTime expiredAt) {
        EmailVerification verification = EmailVerification.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .token(UUID.randomUUID().toString())
                .purpose(purpose)
                .expiredAt(expiredAt)
                .build();
        authMapper.insertEmailVerification(verification);
        return verification;
    }

    private TokenResponse issueTokens(User user, LoginRequestContext context) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = UUID.randomUUID().toString();
        // access token은 stateless JWT이고, refresh token은 DB에서 기기/접속 정보와 함께 관리한다.
        authMapper.insertRefreshToken(RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .expiredAt(LocalDateTime.now().plusSeconds(props.getJwt().getRefreshTokenValiditySeconds()))
                .ipAddress(context != null ? truncate(context.ipAddress(), 45) : null)
                .userAgent(context != null ? truncate(context.userAgent(), 500) : null)
                .build());
        return new TokenResponse(accessToken, refreshToken, "Bearer",
                jwtTokenProvider.getAccessValiditySeconds(), buildMeResponse(user));
    }

    private MeResponse buildMeResponse(User user) {
        if (!"ADMIN".equals(user.getRole()) && !"SUPER_ADMIN".equals(user.getRole())) {
            return MeResponse.from(user);
        }
        return MeResponse.from(user,
                authMapper.findActivePermissionCodes(user.getId()),
                authMapper.findActivePermissionGroups(user.getId()));
    }

    private User releaseExpiredBlockIfNeeded(User user) {
        if (user != null && STATUS_BLOCKED.equals(user.getStatus())
                && user.getBlockedUntil() != null && !user.getBlockedUntil().isAfter(LocalDateTime.now())) {
            userMapper.activateExpiredBlock(user.getId());
            return userMapper.findById(user.getId());
        }
        return user;
    }

    private void validateLoginAllowed(User user, String loginMethod, String identifier, LoginRequestContext context) {
        if (STATUS_DELETED.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "LOGIN", "LOCAL", loginMethod, identifier, false, "ACCOUNT_DELETED", context);
            throw new BusinessException(ErrorCode.FORBIDDEN, "탈퇴 또는 삭제된 계정입니다.");
        }
        if (STATUS_BLOCKED.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "LOGIN", "LOCAL", loginMethod, identifier, false, "ACCOUNT_BLOCKED", context);
            throw new BusinessException(ErrorCode.FORBIDDEN, blockMessage(user));
        }
        if (STATUS_DORMANT.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "LOGIN", "LOCAL", loginMethod, identifier, false, "ACCOUNT_DORMANT", context);
            throw new BusinessException(ErrorCode.FORBIDDEN, "휴면 계정입니다. 휴면 해제 후 로그인할 수 있습니다.");
        }
    }

    private void validateSocialLoginAllowed(User user, SocialUserInfo info, LoginRequestContext context) {
        String identifier = info.email() != null ? info.email() : info.providerUserId();
        if (STATUS_DELETED.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "LOGIN", info.provider(), "OAUTH", identifier, false,
                    "ACCOUNT_DELETED", context);
            throw new BusinessException(ErrorCode.FORBIDDEN, "탈퇴 또는 삭제된 계정입니다.");
        }
        if (STATUS_BLOCKED.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "LOGIN", info.provider(), "OAUTH", identifier, false,
                    "ACCOUNT_BLOCKED", context);
            throw new BusinessException(ErrorCode.FORBIDDEN, blockMessage(user));
        }
        if (STATUS_DORMANT.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "LOGIN", info.provider(), "OAUTH", identifier, false,
                    "ACCOUNT_DORMANT", context);
            throw new BusinessException(ErrorCode.FORBIDDEN, "휴면 계정입니다. 휴면 해제 후 로그인할 수 있습니다.");
        }
    }

    private void validateTokenAllowed(User user, LoginRequestContext context) {
        if (STATUS_DELETED.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "REFRESH", "LOCAL", "REFRESH_TOKEN", user.getEmail(), false,
                    "ACCOUNT_DELETED", context);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "사용할 수 없는 계정입니다.");
        }
        if (STATUS_BLOCKED.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "REFRESH", "LOCAL", "REFRESH_TOKEN", user.getEmail(), false,
                    "ACCOUNT_BLOCKED", context);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "차단된 계정입니다.");
        }
        if (STATUS_DORMANT.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "REFRESH", "LOCAL", "REFRESH_TOKEN", user.getEmail(), false,
                    "ACCOUNT_DORMANT", context);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "휴면 계정입니다.");
        }
    }

    private void recordLoginHistory(Long userId, String eventType, String authProvider, String loginMethod,
                                    String loginIdentifier, boolean success, String failReason,
                                    LoginRequestContext context) {
        // 실패한 로그인도 원인 분석과 관리자 감사 화면을 위해 user_login_history에 남긴다.
        authMapper.insertLoginHistory(UserLoginHistory.builder()
                .userId(userId)
                .eventType(eventType)
                .authProvider(authProvider)
                .loginMethod(loginMethod)
                .loginIdentifier(truncate(loginIdentifier, 255))
                .success(success)
                .failReason(failReason)
                .ipAddress(context != null ? truncate(context.ipAddress(), 45) : null)
                .userAgent(context != null ? truncate(context.userAgent(), 500) : null)
                .requestUri(context != null ? truncate(context.requestUri(), 255) : null)
                .build());
    }

    private void recordSecurityEvent(Long userId, Long actorUserId, String eventType, String eventStage,
                                     String inputIdentifier, String targetEmail, boolean success,
                                     String failReason, String detailMessage, LoginRequestContext context) {
        securityHistoryService.record(UserSecurityHistory.builder()
                .userId(userId)
                .actorUserId(actorUserId)
                .eventType(eventType)
                .eventStage(eventStage)
                .inputIdentifier(truncate(inputIdentifier, 255))
                .targetEmail(truncate(targetEmail, 255))
                .success(success)
                .failReason(truncate(failReason, 255))
                .detailMessage(truncate(detailMessage, 500))
                .ipAddress(context != null ? truncate(context.ipAddress(), 64) : null)
                .userAgent(context != null ? truncate(context.userAgent(), 512) : null)
                .build());
    }

    private BusinessException invalidLogin() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "아이디/이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    private void requireSignupConsents(RegisterRequest request) {
        if (!Boolean.TRUE.equals(request.termsAgreed()) || !Boolean.TRUE.equals(request.privacyAgreed())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "서비스 이용약관과 개인정보 처리방침 동의가 필요합니다.");
        }
    }

    private void recordSignupConsents(Long userId, RegisterRequest request) {
        insertConsent(userId, ConsentType.TERMS, true, "REGISTER");
        insertConsent(userId, ConsentType.PRIVACY, true, "REGISTER");
        insertConsent(userId, ConsentType.AI_DATA, Boolean.TRUE.equals(request.aiDataAgreed()), "REGISTER");
        insertConsent(userId, ConsentType.RESUME_ANALYSIS, Boolean.TRUE.equals(request.resumeAnalysisAgreed()), "REGISTER");
        insertConsent(userId, ConsentType.MARKETING, Boolean.TRUE.equals(request.marketingAgreed()), "REGISTER");
    }

    private void insertConsent(Long userId, ConsentType consentType, boolean agreed, String source) {
        authMapper.insertUserConsent(UserConsent.builder()
                .userId(userId)
                .consentType(consentType.name())
                .consentVersion(consentType.currentVersion())
                .agreed(agreed)
                .agreedAt(agreed ? LocalDateTime.now() : null)
                .revokedAt(agreed ? null : LocalDateTime.now())
                .source(source)
                .build());
    }

    private String blockMessage(User user) {
        if (user.getBlockedUntil() != null) {
            return "차단된 계정입니다. 해제 예정 시각: " + user.getBlockedUntil();
        }
        if (user.getBlockedReason() != null && !user.getBlockedReason().isBlank()) {
            return "차단된 계정입니다. 사유: " + user.getBlockedReason();
        }
        return "차단된 계정입니다.";
    }

    private String normalizeProvider(String provider) {
        if (provider == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "소셜 제공자가 지정되지 않았습니다.");
        }
        String normalized = provider.trim().toUpperCase(Locale.ROOT);
        if (!socialOAuthService.isSupported(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 소셜 제공자입니다: " + provider);
        }
        return normalized;
    }

    private String normalizeOptionalLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return null;
        }
        String normalized = loginId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9_]{4,50}$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아이디는 영문 소문자, 숫자, 밑줄 4~50자로 입력해 주세요.");
        }
        return normalized;
    }

    private String normalizeLoginId(String loginId) {
        String normalized = normalizeOptionalLoginId(loginId);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아이디를 입력해 주세요.");
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String normalizeLoginIdentifier(String identifier) {
        return identifier == null ? null : identifier.trim().toLowerCase(Locale.ROOT);
    }

    private String loginMethodFor(String identifier) {
        return identifier != null && identifier.contains("@") ? "EMAIL" : "LOGIN_ID";
    }

    private boolean isRecoverableAccountStatus(String status) {
        return !STATUS_DELETED.equals(status) && !STATUS_BLOCKED.equals(status);
    }

    private boolean isTemporaryEmail(String email) {
        return email == null || email.isBlank() || email.toLowerCase(Locale.ROOT).endsWith("@social.careertuner");
    }

    private String maskLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return "";
        }
        int length = loginId.length();
        if (length <= 2) {
            return "*".repeat(length);
        }
        if (length <= 4) {
            return loginId.substring(0, 1) + "*".repeat(length - 2) + loginId.substring(length - 1);
        }
        int front = Math.min(3, Math.max(1, length / 3));
        int back = length >= 7 ? 2 : 1;
        int maskLength = Math.max(1, length - front - back);
        return loginId.substring(0, front) + "*".repeat(maskLength) + loginId.substring(length - back);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
