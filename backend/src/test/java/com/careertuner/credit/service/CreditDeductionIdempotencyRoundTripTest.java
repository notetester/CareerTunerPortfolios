package com.careertuner.credit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.careertuner.credit.dto.CreditDeductionResult;

/** 같은 AI 사용 로그 정산이 동시에 들어와도 크레딧과 원장을 한 번만 반영하는지 검증한다. */
@SpringBootTest
class CreditDeductionIdempotencyRoundTripTest {

    @Autowired CreditService creditService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentSameUsageLogDeductsCreditOnlyOnce() throws Exception {
        String email = "rt.credit.idempotent@ct.test";
        jdbc.update("DELETE FROM users WHERE email = ?", email);
        jdbc.update("INSERT INTO users(email, name, role, status, plan, credit, activity_point, user_level, "
                + "email_verified, password_enabled) VALUES(?, '크레딧테스트', 'USER', 'ACTIVE', 'FREE', "
                + "10, 0, 1, 1, 1)", email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO ai_usage_log(user_id, feature_type, status, model, input_tokens, output_tokens, "
                + "token_usage, credit_used) VALUES(?, 'JOB_ANALYSIS', 'SUCCESS', 'test', 1, 1, 2, 3)", userId);
        Long usageLogId = jdbc.queryForObject("SELECT MAX(id) FROM ai_usage_log WHERE user_id = ?", Long.class, userId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var request = (java.util.concurrent.Callable<CreditDeductionResult>) () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return creditService.deductByAiUsageLog(usageLogId, 3);
            };
            Future<CreditDeductionResult> first = executor.submit(request);
            Future<CreditDeductionResult> second = executor.submit(request);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<CreditDeductionResult> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results).filteredOn(CreditDeductionResult::deducted).hasSize(1);
            assertThat(results).filteredOn(result -> "ALREADY_DEDUCTED".equals(result.reason())).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT credit FROM users WHERE id = ?", Integer.class, userId)).isEqualTo(7);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM credit_transaction WHERE ai_usage_log_id = ? AND type = 'AI_USAGE'",
                    Integer.class,
                    usageLogId)).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }
}
