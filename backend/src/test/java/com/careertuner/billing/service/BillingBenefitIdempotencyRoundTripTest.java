package com.careertuner.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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

import com.careertuner.billing.dto.BenefitConsumeResult;
import com.careertuner.common.exception.BusinessException;

/** 같은 AI 사용권 요청이 동시에 재전송돼도 잔액을 한 번만 차감하는지 실 DB 잠금으로 검증한다. */
@SpringBootTest
class BillingBenefitIdempotencyRoundTripTest {

    @Autowired AiBenefitUsageService benefitUsageService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void concurrentSameReferenceConsumesTicketOnlyOnce() throws Exception {
        String email = "rt.billing.idempotent@ct.test";
        jdbc.update("DELETE FROM users WHERE email = ?", email);
        jdbc.update("INSERT INTO users(email, name, role, status, plan, credit, activity_point, user_level, "
                + "email_verified, password_enabled) VALUES(?, '과금테스트', 'USER', 'ACTIVE', 'BASIC', "
                + "0, 0, 1, 1, 1)", email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        LocalDateTime periodStart = LocalDateTime.now().minusDays(1).withNano(0);
        LocalDateTime periodEnd = periodStart.plusMonths(1);
        jdbc.update("INSERT INTO user_subscription(user_id, plan_code, status, started_at, current_period_start, "
                        + "current_period_end) VALUES(?, 'BASIC', 'ACTIVE', ?, ?, ?)",
                userId, periodStart, periodStart, periodEnd);
        jdbc.update("INSERT INTO user_benefit_balance(user_id, benefit_code, period_start, period_end, "
                        + "granted_quantity, used_quantity, remaining_quantity, source_plan_code, source_type, source_code) "
                        + "VALUES(?, 'APPLICATION_ANALYSIS', ?, ?, 2, 0, 2, 'BASIC', 'PLAN', 'BASIC')",
                userId, periodStart, periodEnd);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var request = (java.util.concurrent.Callable<BenefitConsumeResult>) () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return benefitUsageService.consumeByFeature(
                        userId, "JOB_ANALYSIS", "APPLICATION_CASE", 778899L, null, "동시 멱등 테스트");
            };
            Future<BenefitConsumeResult> first = executor.submit(request);
            Future<BenefitConsumeResult> second = executor.submit(request);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<BenefitConsumeResult> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results).filteredOn(BenefitConsumeResult::consumed).hasSize(1);
            assertThat(results).filteredOn(result -> "ALREADY_CONSUMED".equals(result.reason())).hasSize(1);
            assertThat(jdbc.queryForObject(
                    "SELECT remaining_quantity FROM user_benefit_balance "
                            + "WHERE user_id = ? AND benefit_code = 'APPLICATION_ANALYSIS' AND period_start = ?",
                    Integer.class,
                    userId,
                    periodStart)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM benefit_transaction WHERE benefit_code = 'APPLICATION_ANALYSIS' "
                            + "AND transaction_type = 'CONSUME' AND ref_type = 'APPLICATION_CASE' AND ref_id = 778899",
                    Integer.class)).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void insufficientTicketRollsBackSavepointButAllowsCallerCreditFallbackToCommit() {
        String email = "rt.billing.fallback@ct.test";
        jdbc.update("DELETE FROM users WHERE email = ?", email);
        jdbc.update("INSERT INTO users(email, name, role, status, plan, credit, activity_point, user_level, "
                + "email_verified, password_enabled) VALUES(?, '폴백 전', 'USER', 'ACTIVE', 'BASIC', "
                + "10, 0, 1, 1, 1)", email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        LocalDateTime periodStart = LocalDateTime.now().minusDays(1).withNano(0);
        LocalDateTime periodEnd = periodStart.plusMonths(1);
        jdbc.update("INSERT INTO user_subscription(user_id, plan_code, status, started_at, current_period_start, "
                        + "current_period_end) VALUES(?, 'BASIC', 'ACTIVE', ?, ?, ?)",
                userId, periodStart, periodStart, periodEnd);
        jdbc.update("INSERT INTO user_benefit_balance(user_id, benefit_code, period_start, period_end, "
                        + "granted_quantity, used_quantity, remaining_quantity, source_plan_code, source_type, source_code) "
                        + "VALUES(?, 'APPLICATION_ANALYSIS', ?, ?, 0, 0, 0, 'BASIC', 'PLAN', 'BASIC')",
                userId, periodStart, periodEnd);

        AtomicBoolean insufficient = new AtomicBoolean();
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.update("UPDATE users SET name = '폴백 완료' WHERE id = ?", userId);
                try {
                    benefitUsageService.consumeByFeature(
                            userId, "JOB_ANALYSIS", "APPLICATION_CASE", 778900L, null, "폴백 savepoint 테스트");
                } catch (BusinessException expected) {
                    insufficient.set(true);
                    jdbc.update("UPDATE users SET credit = credit - 2 WHERE id = ?", userId);
                }
            });

            assertThat(insufficient).isTrue();
            assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, userId))
                    .isEqualTo("폴백 완료");
            assertThat(jdbc.queryForObject("SELECT credit FROM users WHERE id = ?", Integer.class, userId))
                    .isEqualTo(8);
        } finally {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }
}
