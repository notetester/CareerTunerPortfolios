package com.careertuner.loginrisk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.loginrisk.domain.LoginRiskPolicy;
import com.careertuner.loginrisk.mapper.LoginRiskPolicyMapper;
import com.careertuner.loginrisk.service.LoginRiskPolicyService;

/**
 * 로그인 위험도 정책 <b>실 DB round-trip</b> — 콘솔 저장값이 team1_db 에 영속되고, AuthServiceImpl 이
 * 읽는 getter 가 코드 기본값(5/10)이 아니라 DB 저장값을 반영하는지 증명. {@code @Transactional} 롤백.
 */
@SpringBootTest
@Transactional
class LoginRiskPolicyRoundTripTest {

    @Autowired
    LoginRiskPolicyService service;
    @Autowired
    LoginRiskPolicyMapper mapper;

    @Test
    void update_persistsToRealDb_andAuthReadsIt() {
        assertThat(mapper.findPolicy()).as("login_risk_policy 행이 실제 DB에 존재해야 한다").isNotNull();

        service.update(true, 8, 42, null); // 기본값(5/10)과 다른 distinctive 값

        LoginRiskPolicy persisted = mapper.findPolicy();
        assertThat(persisted.maxFailedCount()).isEqualTo(8);
        assertThat(persisted.lockMinutes()).isEqualTo(42);

        // AuthServiceImpl 가 로그인 실패 잠금에서 읽는 getter 들
        assertThat(service.isLockoutEnabled()).isTrue();
        assertThat(service.getMaxFailedCount()).isEqualTo(8);
        assertThat(service.getLockMinutes()).isEqualTo(42);
    }
}
