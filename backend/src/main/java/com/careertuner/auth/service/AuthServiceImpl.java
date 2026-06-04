package com.careertuner.auth.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.auth.domain.EmailVerification;
import com.careertuner.auth.domain.RefreshToken;
import com.careertuner.auth.domain.UserSocial;
import com.careertuner.auth.dto.LoginRequest;
import com.careertuner.auth.dto.MeResponse;
import com.careertuner.auth.dto.RegisterRequest;
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

    private final UserMapper userMapper;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final SocialOAuthService socialOAuthService;
    private final CareerTunerProperties props;

    // ════════════════════════════════════════════ 이메일 회원가입/로그인

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userMapper.countByEmail(request.email()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다.");
        }
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .passwordEnabled(true)
                .name(request.name())
                .emailVerified(false)
                .userType("JOB_SEEKER")
                .role("USER")
                .status("ACTIVE")
                .plan("FREE")
                .credit(0)
                .build();
        userMapper.insert(user);
        issueEmailVerification(user);
        return issueTokens(user);
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userMapper.findByEmail(request.email());
        if (user == null || !user.isPasswordEnabled() || user.getPassword() == null
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        if ("BLOCKED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "차단된 계정입니다.");
        }
        userMapper.touchLastLogin(user.getId());
        return issueTokens(user);
    }

    // ════════════════════════════════════════════ 토큰

    @Override
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshToken stored = authMapper.findRefreshToken(refreshToken);
        if (stored == null || stored.isRevoked() || stored.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다.");
        }
        User user = userMapper.findById(stored.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "사용자를 찾을 수 없습니다.");
        }
        authMapper.revokeRefreshToken(refreshToken); // rotation
        return issueTokens(user);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authMapper.revokeRefreshToken(refreshToken);
        }
    }

    // ════════════════════════════════════════════ 이메일 인증

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
        User user = userMapper.findByEmail(email);
        if (user == null || user.isEmailVerified()) {
            return; // 계정 존재 여부를 노출하지 않음 / 이미 인증됨
        }
        issueEmailVerification(user);
    }

    @Override
    public boolean isEmailTaken(String email) {
        return userMapper.countByEmail(email) > 0;
    }

    @Override
    public MeResponse me(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return MeResponse.from(user);
    }

    // ════════════════════════════════════════════ 소셜 로그인

    @Override
    public String buildAuthorizationUrl(String provider) {
        String normalized = normalizeProvider(provider);
        String state = jwtTokenProvider.createOauthState(normalized);
        return socialOAuthService.getAuthorizationUrl(normalized, state);
    }

    @Override
    @Transactional
    public TokenResponse handleOAuthCallback(String provider, String code, String state) {
        String normalized = normalizeProvider(provider);
        if (!jwtTokenProvider.validateOauthState(state, normalized)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "잘못된 접근입니다(OAuth state).");
        }
        SocialUserInfo info = socialOAuthService.fetchUserInfo(normalized, code, state);
        User user = findOrCreateSocialUser(info);
        return issueTokens(user);
    }

    // ════════════════════════════════════════════ 내부

    private User findOrCreateSocialUser(SocialUserInfo info) {
        UserSocial social = authMapper.findSocial(info.provider(), info.providerUserId());
        if (social != null) {
            return userMapper.findById(social.getUserId());
        }
        User user = null;
        if (info.email() != null && !info.email().isBlank()) {
            user = userMapper.findByEmail(info.email());
        }
        if (user == null) {
            String email = (info.email() != null && !info.email().isBlank())
                    ? info.email()
                    : info.provider().toLowerCase() + "_" + info.providerUserId() + "@social.careertuner";
            if (userMapper.countByEmail(email) > 0) {
                email = info.provider().toLowerCase() + "_" + info.providerUserId()
                        + "_" + System.currentTimeMillis() + "@social.careertuner";
            }
            user = User.builder()
                    .email(email)
                    .password(null)
                    .passwordEnabled(false)
                    .name(info.name())
                    .emailVerified(true) // 제공자가 검증한 계정으로 간주
                    .userType("JOB_SEEKER")
                    .role("USER")
                    .status("ACTIVE")
                    .plan("FREE")
                    .credit(0)
                    .build();
            userMapper.insert(user);
        }
        authMapper.insertSocial(UserSocial.builder()
                .userId(user.getId())
                .provider(info.provider())
                .providerUserId(info.providerUserId())
                .build());
        return user;
    }

    private void issueEmailVerification(User user) {
        String token = UUID.randomUUID().toString();
        authMapper.insertEmailVerification(EmailVerification.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .token(token)
                .purpose("VERIFY")
                .expiredAt(LocalDateTime.now().plusHours(24))
                .build());
        emailService.sendVerificationEmail(user.getEmail(), token);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = UUID.randomUUID().toString();
        authMapper.insertRefreshToken(RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .expiredAt(LocalDateTime.now().plusSeconds(props.getJwt().getRefreshTokenValiditySeconds()))
                .build());
        return TokenResponse.of(accessToken, refreshToken, jwtTokenProvider.getAccessValiditySeconds(), user);
    }

    private String normalizeProvider(String provider) {
        if (provider == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "소셜 제공자가 지정되지 않았습니다.");
        }
        String normalized = provider.trim().toUpperCase();
        if (!socialOAuthService.isSupported(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 소셜 제공자: " + provider);
        }
        return normalized;
    }
}
