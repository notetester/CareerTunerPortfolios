package com.careertuner.reward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.reward.dto.RewardGrantResult;
import com.careertuner.reward.service.RewardService;

/** 리워드 실패가 결제/콘텐츠 같은 호출자 트랜잭션까지 롤백하지 않는지 실제 savepoint로 검증한다. */
@SpringBootTest
class RewardNestedTransactionRoundTripTest {

    @Autowired RewardService rewardService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void failedRewardRollsBackOnlyNestedWorkAndKeepsCallerTransaction() {
        String email = "rt.reward.nested@ct.test";
        jdbc.update("DELETE FROM users WHERE email = ?", email);
        jdbc.update("INSERT INTO users(email, name, role, status, plan, credit, activity_point, user_level, "
                + "email_verified, password_enabled) VALUES(?, '원래 이름', 'USER', 'ACTIVE', 'FREE', "
                + "2147483647, 0, 1, 1, 1)", email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);

        AtomicBoolean rewardFailed = new AtomicBoolean();
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.update("UPDATE users SET name = '호출 작업 완료' WHERE id = ?", userId);
                try {
                    // APPLICATION_CASE_READY는 크레딧 1을 지급한다. INT 최대 잔액에서는 의도적으로 실패한다.
                    rewardService.grant(userId, "APPLICATION_CASE_READY", "APPLICATION_CASE", 777L);
                } catch (BusinessException expected) {
                    rewardFailed.set(true);
                }
            });

            assertThat(rewardFailed).isTrue();
            assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, userId))
                    .isEqualTo("호출 작업 완료");
            assertThat(jdbc.queryForObject("SELECT activity_point FROM users WHERE id = ?", Integer.class, userId))
                    .isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_reward_history WHERE user_id = ? AND event_code = 'APPLICATION_CASE_READY'",
                    Integer.class,
                    userId)).isZero();
        } finally {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void concurrentDailyLoginRespectsSingleDailyGrant() throws Exception {
        String email = "rt.reward.daily.concurrent@ct.test";
        jdbc.update("DELETE FROM users WHERE email = ?", email);
        jdbc.update("INSERT INTO users(email, name, role, status, plan, credit, activity_point, user_level, "
                + "email_verified, password_enabled) VALUES(?, '동시로그인', 'USER', 'ACTIVE', 'FREE', "
                + "0, 0, 1, 1, 1)", email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var request = (java.util.concurrent.Callable<RewardGrantResult>) () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return rewardService.grant(userId, "DAILY_LOGIN", "LOGIN", null);
            };
            Future<RewardGrantResult> first = executor.submit(request);
            Future<RewardGrantResult> second = executor.submit(request);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<RewardGrantResult> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results).filteredOn(RewardGrantResult::applied).hasSize(1);
            assertThat(results).filteredOn(result -> "DAILY_CAP".equals(result.skipReason())).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT activity_point FROM users WHERE id = ?", Integer.class, userId))
                    .isEqualTo(5);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_reward_history WHERE user_id = ? AND event_code = 'DAILY_LOGIN'",
                    Integer.class,
                    userId)).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }
}
