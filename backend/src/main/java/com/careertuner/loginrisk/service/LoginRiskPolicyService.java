package com.careertuner.loginrisk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.loginrisk.domain.LoginRiskPolicy;
import com.careertuner.loginrisk.mapper.LoginRiskPolicyMapper;

import jakarta.annotation.PostConstruct;

/**
 * 로그인 위험도(브루트포스) 잠금 정책 캐싱 서비스.
 *
 * <p>로그인 실패 hot-path 에서 매번 DB 를 치지 않도록 volatile 캐시로 보관한다. 코드 기본값은
 * 기존 {@code AuthServiceImpl} 상수(enabled=true, 5회, 10분)와 동일해, 정책이 없거나 미적용
 * DB 여도 도입 전과 동작이 같다. 관리자 편집 시 DB 갱신 + 캐시 즉시 반영.</p>
 */
@Service
public class LoginRiskPolicyService {

    private static final Logger log = LoggerFactory.getLogger(LoginRiskPolicyService.class);
    private static final int ID = 1;

    // 코드 기본값 = 기존 AuthServiceImpl 상수 (도입 시 동작 무변경)
    static final boolean DEFAULT_ENABLED = true;
    static final int DEFAULT_MAX_FAILED = 5;
    static final int DEFAULT_LOCK_MINUTES = 10;

    private final LoginRiskPolicyMapper mapper;

    private volatile boolean lockoutEnabled = DEFAULT_ENABLED;
    private volatile int maxFailedCount = DEFAULT_MAX_FAILED;
    private volatile int lockMinutes = DEFAULT_LOCK_MINUTES;

    public LoginRiskPolicyService(LoginRiskPolicyMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    void init() {
        try {
            LoginRiskPolicy p = mapper.findPolicy();
            if (p != null) {
                applyToCache(p.enabled(), p.maxFailedCount(), p.lockMinutes());
            }
        } catch (Exception e) {
            // login_risk_policy(patches/20260706c) 미적용 DB 에서도 코드 기본값으로 강등(앱 기동 보장).
            log.warn("로그인 위험도 정책 로드 실패 — 코드 기본값 사용(20260706c 미적용 가능): {}", e.getMessage());
        }
        log.info("로그인 위험도 정책: enabled={}, maxFailedCount={}, lockMinutes={}",
                lockoutEnabled, maxFailedCount, lockMinutes);
    }

    private void applyToCache(boolean enabled, int max, int minutes) {
        this.lockoutEnabled = enabled;
        this.maxFailedCount = max;
        this.lockMinutes = minutes;
    }

    /* ── 로그인 hot-path 조회(캐시) ── */

    public boolean isLockoutEnabled() {
        return lockoutEnabled;
    }

    public int getMaxFailedCount() {
        return maxFailedCount;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    /* ── 관리자 콘솔 ── */

    /** 현재 정책(미적용 DB 면 캐시 스냅샷). */
    public LoginRiskPolicy getCurrent() {
        try {
            LoginRiskPolicy p = mapper.findPolicy();
            if (p != null) {
                return p;
            }
        } catch (Exception e) {
            log.warn("로그인 위험도 정책 조회 실패 — 캐시 스냅샷 반환(20260706c 미적용 가능): {}", e.getMessage());
        }
        return new LoginRiskPolicy(ID, lockoutEnabled, maxFailedCount, lockMinutes, null, null, null);
    }

    /** 정책 저장 + 캐시 즉시 반영. */
    public LoginRiskPolicy update(boolean enabled, int newMaxFailedCount, int newLockMinutes, Long actorId) {
        mapper.updatePolicy(enabled, newMaxFailedCount, newLockMinutes, actorId);
        applyToCache(enabled, newMaxFailedCount, newLockMinutes);
        log.info("로그인 위험도 정책 변경: enabled={}, maxFailedCount={}, lockMinutes={}, actor={}",
                enabled, newMaxFailedCount, newLockMinutes, actorId);
        return getCurrent();
    }
}
