package com.careertuner.reward;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.reward.domain.UserCoupon;
import com.careertuner.reward.domain.UserRewardAccount;
import com.careertuner.reward.dto.CouponRedeemResult;
import com.careertuner.reward.dto.RewardGrantResult;
import com.careertuner.reward.mapper.RewardMapper;
import com.careertuner.reward.service.CouponService;
import com.careertuner.reward.service.RewardService;

/**
 * 리워드 이코노미 <b>실 DB round-trip</b> — team1_db 상대로 적립/레벨업/일일캡/쿠폰을 검증.
 * {@code @Transactional} 롤백으로 공유 DB 오염 없음. 규칙 on/off 양상태를 직접 검증한다.
 */
@SpringBootTest
@Transactional
class RewardEconomyRoundTripTest {

    @Autowired RewardService rewardService;
    @Autowired CouponService couponService;
    @Autowired RewardMapper rewardMapper;
    @Autowired JdbcTemplate jdbc;

    private Long createUser(String email) {
        jdbc.update("INSERT INTO users(email, name, role, status, plan, credit, activity_point, user_level, email_verified, password_enabled) "
                + "VALUES(?, ?, 'USER', 'ACTIVE', 'FREE', 0, 0, 1, 1, 1)", email, "리워드테스트");
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    @Test
    void grant_appliesPointsAndRecordsHistory_whenRuleEnabled() {
        Long userId = createUser("rt.reward.on@ct.test");

        RewardGrantResult result = rewardService.grant(userId, "COMMUNITY_POST_CREATE", "POST", 1L);

        assertThat(result.applied()).isTrue();
        assertThat(result.pointsGranted()).isEqualTo(10);
        UserRewardAccount account = rewardMapper.findAccount(userId);
        assertThat(account.getActivityPoint()).isEqualTo(10);
        assertThat(rewardMapper.findRecentHistoryByUser(userId, 10)).hasSize(1);
    }

    @Test
    void grant_skips_whenRuleDisabled() {
        Long userId = createUser("rt.reward.off@ct.test");
        // 관리자가 규칙을 off 로 둔 상태를 재현
        jdbc.update("UPDATE reward_rule SET enabled = 0 WHERE event_code = 'COMMUNITY_POST_CREATE'");

        RewardGrantResult result = rewardService.grant(userId, "COMMUNITY_POST_CREATE", "POST", 1L);

        assertThat(result.applied()).isFalse();
        assertThat(result.skipReason()).isEqualTo("NO_RULE");
        assertThat(rewardMapper.findAccount(userId).getActivityPoint()).isZero();
    }

    @Test
    void grant_levelsUp_andGrantsLevelupReward_whenThresholdCrossed() {
        Long userId = createUser("rt.reward.levelup@ct.test");
        // 레벨2 임계(100p) 직전으로 만들어 글 작성 1회로 레벨업되게 한다.
        jdbc.update("UPDATE users SET activity_point = 95 WHERE id = ?", userId);

        RewardGrantResult result = rewardService.grant(userId, "COMMUNITY_POST_CREATE", "POST", 1L);

        assertThat(result.leveledUp()).isTrue();
        assertThat(result.levelAfter()).isGreaterThanOrEqualTo(2);
        UserRewardAccount account = rewardMapper.findAccount(userId);
        assertThat(account.getUserLevel()).isGreaterThanOrEqualTo(2);
        // 레벨업 보상 크레딧이 원장에 반영되어 잔액이 0보다 크다(레벨2 보상 5크레딧).
        assertThat(account.getCredit()).isGreaterThan(0);
    }

    @Test
    void grant_respectsDailyCap() {
        Long userId = createUser("rt.reward.cap@ct.test");

        RewardGrantResult first = rewardService.grant(userId, "DAILY_LOGIN", "LOGIN", null);
        RewardGrantResult second = rewardService.grant(userId, "DAILY_LOGIN", "LOGIN", null);

        assertThat(first.applied()).isTrue();
        assertThat(second.applied()).isFalse();
        assertThat(second.skipReason()).isEqualTo("DAILY_CAP");
        assertThat(rewardMapper.findAccount(userId).getActivityPoint()).isEqualTo(5);
    }

    @Test
    void grant_replaysConcreteReferenceWithoutDuplicatingReward() {
        Long userId = createUser("rt.reward.idempotent@ct.test");

        RewardGrantResult first = rewardService.grant(userId, "CREDIT_PURCHASE", "PAYMENT", 991L);
        RewardGrantResult replay = rewardService.grant(userId, "CREDIT_PURCHASE", "PAYMENT", 991L);

        assertThat(first.applied()).isTrue();
        assertThat(replay.applied()).isFalse();
        assertThat(replay.skipReason()).isEqualTo("ALREADY_GRANTED");
        assertThat(rewardMapper.findAccount(userId).getActivityPoint()).isEqualTo(50);
        assertThat(rewardMapper.findRecentHistoryByUser(userId, 10))
                .filteredOn(history -> "CREDIT_PURCHASE".equals(history.getEventCode()))
                .hasSize(1);
    }

    @Test
    void coupon_issueAndRedeemCredit_grantsCreditAndMarksUsed() {
        Long userId = createUser("rt.reward.coupon@ct.test");

        UserCoupon issued = couponService.issue(userId, "LEVELUP_PRO"); // CREDIT 20
        assertThat(issued.getStatus()).isEqualTo("ISSUED");

        CouponRedeemResult redeem = couponService.redeem(userId, "LEVELUP_PRO");
        assertThat(redeem.creditGranted()).isEqualTo(20);
        assertThat(redeem.balanceAfter()).isEqualTo(20);
        assertThat(rewardMapper.findAccount(userId).getCredit()).isEqualTo(20);
        // 재사용 불가(이미 사용됨)
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> couponService.redeem(userId, "LEVELUP_PRO"))
                .isInstanceOf(com.careertuner.common.exception.BusinessException.class);
    }
}
