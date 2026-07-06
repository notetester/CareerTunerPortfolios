package com.careertuner.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.careertuner.activitylog.service.SecurityHistoryService;
import com.careertuner.auth.dto.LoginRequest;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.JwtTokenProvider;
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

    private final AuthServiceImpl service = new AuthServiceImpl(
            userMapper, authMapper, passwordEncoder, jwtTokenProvider, emailService,
            socialOAuthService, props, securityHistoryService, loginRiskPolicyService);

    private static final String EMAIL = "user@test.com";

    /** ACTIVE 계정에서 틀린 비밀번호로 로그인 시도 → 항상 invalidLogin 예외. failedCount 는 "이번 실패 직전"의 누적. */
    private void attemptWrongPassword(int priorFailedCount) {
        when(userMapper.findByEmail(EMAIL)).thenReturn(User.builder()
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
}
