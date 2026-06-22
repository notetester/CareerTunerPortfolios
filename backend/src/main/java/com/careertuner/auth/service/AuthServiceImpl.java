package com.careertuner.auth.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.auth.domain.EmailVerification;
import com.careertuner.auth.domain.RefreshToken;
import com.careertuner.auth.domain.UserConsent;
import com.careertuner.auth.domain.UserLoginHistory;
import com.careertuner.auth.domain.UserSocial;
import com.careertuner.auth.dto.LoginRequest;
import com.careertuner.auth.dto.LoginRequestContext;
import com.careertuner.auth.dto.MeResponse;
import com.careertuner.auth.dto.PasswordResetConfirmRequest;
import com.careertuner.auth.dto.PasswordResetRequest;
import com.careertuner.auth.dto.RegisterRequest;
import com.careertuner.auth.dto.TokenRequest;
import com.careertuner.auth.dto.TokenResponse;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.JwtTokenProvider;
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
    private static final int MAX_FAILED_LOGIN_COUNT = 5;
    private static final int FAILED_LOGIN_LOCK_MINUTES = 10;

    private final UserMapper userMapper;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final SocialOAuthService socialOAuthService;
    private final CareerTunerProperties props;

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request, LoginRequestContext context) {
        String email = normalizeEmail(request.email());
        if (userMapper.countByEmail(email) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다.");
        }
        requireSignupConsents(request);

        User user = User.builder()
                .email(email)
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

        issueEmailVerification(user);
        // 회원가입 직후 자동 로그인 정책이므로 로그인 성공과 동일하게 접속 정보를 남긴다.
        userMapper.touchLastLoginAndResetFailures(user.getId());
        recordLoginHistory(user.getId(), "LOGIN", "LOCAL", "EMAIL", email, true, null, context);
        return issueTokens(user, context);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse login(LoginRequest request, LoginRequestContext context) {
        String email = normalizeEmail(request.email());
        User user = userMapper.findByEmail(email);
        if (user == null) {
            recordLoginHistory(null, "LOGIN", "LOCAL", "EMAIL", email, false, "USER_NOT_FOUND", context);
            throw invalidLogin();
        }

        user = releaseExpiredBlockIfNeeded(user);
        // 차단/휴면/삭제 계정에는 토큰을 발급하지 않기 위해 비밀번호 검증 전에 상태를 먼저 본다.
        validateLoginAllowed(user, email, context);

        if (!user.isPasswordEnabled() || user.getPassword() == null) {
            recordLoginHistory(user.getId(), "LOGIN", "LOCAL", "EMAIL", email, false,
                    "PASSWORD_LOGIN_DISABLED", context);
            throw invalidLogin();
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            userMapper.increaseFailedLogin(user.getId());
            recordLoginHistory(user.getId(), "LOGIN", "LOCAL", "EMAIL", email, false, "WRONG_PASSWORD", context);
            if (user.getFailedLoginCount() + 1 >= MAX_FAILED_LOGIN_COUNT) {
                LocalDateTime blockedUntil = LocalDateTime.now().plusMinutes(FAILED_LOGIN_LOCK_MINUTES);
                String reason = "로그인 실패 " + MAX_FAILED_LOGIN_COUNT + "회 초과";
                userMapper.lockForFailedLogin(user.getId(), blockedUntil, reason);
                authMapper.revokeAllForUser(user.getId());
                authMapper.insertUserStatusHistory(user.getId(), null, user.getStatus(), STATUS_BLOCKED,
                        reason, "자동 계정 잠금", blockedUntil);
            }
            throw invalidLogin();
        }

        userMapper.touchLastLoginAndResetFailures(user.getId());
        recordLoginHistory(user.getId(), "LOGIN", "LOCAL", "EMAIL", email, true, null, context);
        return issueTokens(user, context);
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
        if (ev == null || ev.isUsed() || !"VERIFY".equals(ev.getPurpose())
                || ev.getExpiredAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        authMapper.markEmailVerificationUsed(ev.getId());
        if (ev.getUserId() != null) {
            userMapper.markEmailVerified(ev.getUserId());
        }
        return true;
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
    public void requestPasswordReset(PasswordResetRequest request, LoginRequestContext context) {
        String email = normalizeEmail(request.email());
        User user = userMapper.findByEmail(email);
        if (user == null || STATUS_DELETED.equals(user.getStatus())) {
            recordLoginHistory(user != null ? user.getId() : null, "PASSWORD_RESET", "LOCAL", "EMAIL",
                    email, false, user == null ? "USER_NOT_FOUND" : "ACCOUNT_DELETED", context);
            throw new BusinessException(ErrorCode.NOT_FOUND, "등록되지 않은 이메일입니다.");
        }
        recordLoginHistory(user.getId(), "PASSWORD_RESET", "LOCAL", "EMAIL", email, true, null, context);
        EmailVerification verification = issueEmailVerification(user, "RESET_PW", 1);
        emailService.sendPasswordResetEmail(user.getEmail(), verification.getToken());
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
    }

    @Override
    @Transactional
    public void requestDormantRelease(PasswordResetRequest request, LoginRequestContext context) {
        String email = normalizeEmail(request.email());
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
    public MeResponse me(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return MeResponse.from(user);
    }

    @Override
    public String buildAuthorizationUrl(String provider) {
        String normalized = normalizeProvider(provider);
        String state = jwtTokenProvider.createOauthState(normalized);
        return socialOAuthService.getAuthorizationUrl(normalized, state);
    }

    @Override
    @Transactional
    public TokenResponse handleOAuthCallback(String provider, String code, String state, LoginRequestContext context) {
        String normalized = normalizeProvider(provider);
        if (!jwtTokenProvider.validateOauthState(state, normalized)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "잘못된 OAuth state입니다.");
        }

        SocialUserInfo info = socialOAuthService.fetchUserInfo(normalized, code, state);
        User user = findOrCreateSocialUser(info);
        user = releaseExpiredBlockIfNeeded(user);
        validateSocialLoginAllowed(user, info, context);

        userMapper.touchLastLoginAndResetFailures(user.getId());
        String identifier = info.email() != null ? info.email() : info.providerUserId();
        recordLoginHistory(user.getId(), "LOGIN", info.provider(), "OAUTH", identifier, true, null, context);
        return issueTokens(user, context);
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
            user = User.builder()
                    .email(email)
                    .password(null)
                    .passwordEnabled(false)
                    .name(info.name() != null && !info.name().isBlank() ? info.name() : info.provider() + " 사용자")
                    .emailVerified(true)
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

    private void issueEmailVerification(User user) {
        EmailVerification verification = issueEmailVerification(user, "VERIFY", 24);
        emailService.sendVerificationEmail(user.getEmail(), verification.getToken());
    }

    private EmailVerification issueEmailVerification(User user, String purpose, int validHours) {
        EmailVerification verification = EmailVerification.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .token(UUID.randomUUID().toString())
                .purpose(purpose)
                .expiredAt(LocalDateTime.now().plusHours(validHours))
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
        return TokenResponse.of(accessToken, refreshToken, jwtTokenProvider.getAccessValiditySeconds(), user);
    }

    private User releaseExpiredBlockIfNeeded(User user) {
        if (user != null && STATUS_BLOCKED.equals(user.getStatus())
                && user.getBlockedUntil() != null && !user.getBlockedUntil().isAfter(LocalDateTime.now())) {
            userMapper.activateExpiredBlock(user.getId());
            return userMapper.findById(user.getId());
        }
        return user;
    }

    private void validateLoginAllowed(User user, String identifier, LoginRequestContext context) {
        if (STATUS_DELETED.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "LOGIN", "LOCAL", "EMAIL", identifier, false, "ACCOUNT_DELETED", context);
            throw new BusinessException(ErrorCode.FORBIDDEN, "탈퇴 또는 삭제된 계정입니다.");
        }
        if (STATUS_BLOCKED.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "LOGIN", "LOCAL", "EMAIL", identifier, false, "ACCOUNT_BLOCKED", context);
            throw new BusinessException(ErrorCode.FORBIDDEN, blockMessage(user));
        }
        if (STATUS_DORMANT.equals(user.getStatus())) {
            recordLoginHistory(user.getId(), "LOGIN", "LOCAL", "EMAIL", identifier, false, "ACCOUNT_DORMANT", context);
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

    private BusinessException invalidLogin() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    private void requireSignupConsents(RegisterRequest request) {
        if (!Boolean.TRUE.equals(request.termsAgreed()) || !Boolean.TRUE.equals(request.privacyAgreed())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "서비스 이용약관과 개인정보 처리방침 동의가 필요합니다.");
        }
    }

    private void recordSignupConsents(Long userId, RegisterRequest request) {
        insertConsent(userId, "TERMS", true, "REGISTER");
        insertConsent(userId, "PRIVACY", true, "REGISTER");
        insertConsent(userId, "AI_DATA", Boolean.TRUE.equals(request.aiDataAgreed()), "REGISTER");
        insertConsent(userId, "MARKETING", Boolean.TRUE.equals(request.marketingAgreed()), "REGISTER");
    }

    private void insertConsent(Long userId, String consentType, boolean agreed, String source) {
        authMapper.insertUserConsent(UserConsent.builder()
                .userId(userId)
                .consentType(consentType)
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

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
