package com.careertuner.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.careertuner.payment.dto.TossPaymentConfirmRequest;
import com.careertuner.payment.dto.TossPaymentConfirmResponse;
import com.careertuner.payment.service.TossPaymentClient.ConfirmedPayment;

/** 동일 READY 주문의 동시 성공 콜백을 실제 MySQL 행 잠금과 트랜잭션으로 검증한다. */
@SpringBootTest
class PaymentConfirmConcurrencyRoundTripTest {

    @Autowired PaymentService paymentService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TossPaymentClient tossPaymentClient;

    @Test
    void concurrentSameConfirmApprovesAndGrantsOnlyOnceThenReplays() throws Exception {
        reset(tossPaymentClient);
        String email = "rt.payment.concurrent@ct.test";
        String orderId = "RT-CONCURRENT-" + UUID.randomUUID();
        String paymentKey = "RT-PAYKEY-" + UUID.randomUUID();
        jdbc.update("DELETE FROM users WHERE email = ?", email);
        jdbc.update("INSERT INTO users(email, name, role, status, plan, credit, activity_point, user_level, "
                + "email_verified, password_enabled) VALUES(?, '동시결제', 'USER', 'ACTIVE', 'FREE', "
                + "0, 0, 1, 1, 1)", email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO payment(user_id, provider, product_type, product_code, order_id, payment_key, "
                        + "amount, plan, credit_amount, status) VALUES(?, 'TOSS', 'CREDIT', 'CREDIT_RT', ?, NULL, "
                        + "10000, NULL, 10, 'READY')",
                userId,
                orderId);

        when(tossPaymentClient.confirm(paymentKey, orderId, 10_000)).thenAnswer(invocation -> {
            Thread.sleep(250);
            return new ConfirmedPayment(paymentKey, orderId, 10_000, "DONE");
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(paymentKey, orderId, 10_000);
        try {
            var confirm = (java.util.concurrent.Callable<TossPaymentConfirmResponse>) () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return paymentService.confirm(userId, request);
            };
            Future<TossPaymentConfirmResponse> first = executor.submit(confirm);
            Future<TossPaymentConfirmResponse> second = executor.submit(confirm);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<TossPaymentConfirmResponse> results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS));

            assertThat(results).allSatisfy(result -> {
                assertThat(result.status()).isEqualTo("PAID");
                assertThat(result.paymentKey()).isEqualTo(paymentKey);
                assertThat(result.balance()).isEqualTo(10);
            });
            verify(tossPaymentClient, times(1)).confirm(paymentKey, orderId, 10_000);
            assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE order_id = ?", String.class, orderId))
                    .isEqualTo("PAID");
            assertThat(jdbc.queryForObject("SELECT payment_key FROM payment WHERE order_id = ?", String.class, orderId))
                    .isEqualTo(paymentKey);
            assertThat(jdbc.queryForObject("SELECT credit FROM users WHERE id = ?", Integer.class, userId)).isEqualTo(10);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM credit_transaction WHERE user_id = ? AND type = 'CHARGE'",
                    Integer.class,
                    userId)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_reward_history WHERE user_id = ? AND event_code = 'CREDIT_PURCHASE'",
                    Integer.class,
                    userId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT activity_point FROM users WHERE id = ?", Integer.class, userId))
                    .isEqualTo(50);
        } finally {
            start.countDown();
            executor.shutdownNow();
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
            reset(tossPaymentClient);
        }
    }
}
