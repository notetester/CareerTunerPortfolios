package com.careertuner.ai.common.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** 총 시간예산 공용 헬퍼 — 0/음수/null = 무제한(OFF) 시맨틱과 절삭 동작 검증. */
class AiTotalTimeBudgetTest {

    @Test
    void zeroNegativeAndNullAreUnlimitedOff() {
        for (Duration off : new Duration[] {null, Duration.ZERO, Duration.ofSeconds(-5)}) {
            AiTotalTimeBudget budget = AiTotalTimeBudget.start(off);
            assertThat(budget.unlimited()).isTrue();
            assertThat(budget.expired()).isFalse();
            assertThat(budget.cap(Duration.ofSeconds(60))).isEqualTo(Duration.ofSeconds(60));
            assertThat(budget.capBackoffMs(1500)).isEqualTo(1500);
        }
    }

    @Test
    void positiveBudgetCapsAttemptTimeoutToRemaining() {
        AiTotalTimeBudget budget = AiTotalTimeBudget.start(Duration.ofMillis(200));
        assertThat(budget.unlimited()).isFalse();
        assertThat(budget.expired()).isFalse();
        // 남은 예산(≤200ms)이 per-attempt(60s)보다 작으므로 절삭된다
        assertThat(budget.cap(Duration.ofSeconds(60))).isLessThanOrEqualTo(Duration.ofMillis(200));
        // per-attempt 가 남은 예산보다 작으면 그대로
        assertThat(budget.cap(Duration.ofMillis(1))).isEqualTo(Duration.ofMillis(1));
    }

    @Test
    void expiresAfterBudgetElapses() throws InterruptedException {
        AiTotalTimeBudget budget = AiTotalTimeBudget.start(Duration.ofMillis(30));
        Thread.sleep(60);
        assertThat(budget.expired()).isTrue();
        assertThat(budget.cap(Duration.ofSeconds(60))).isEqualTo(Duration.ofMillis(1)); // 소진 후 최소값
        assertThat(budget.capBackoffMs(5000)).isZero();
    }

    @Test
    void nonPositivePerAttemptIsFlooredOnLimitedPath() {
        // 설정 오류(timeout ≤ 0) 방어 — 예산 ON 이면 1ms 로 강제(HttpRequest.timeout IAE 방지)
        AiTotalTimeBudget budget = AiTotalTimeBudget.start(Duration.ofSeconds(10));
        assertThat(budget.cap(Duration.ZERO)).isEqualTo(Duration.ofMillis(1));
        assertThat(budget.cap(null)).isEqualTo(Duration.ofMillis(1));
    }

    @Test
    void backoffIsCappedByRemainingBudget() {
        AiTotalTimeBudget budget = AiTotalTimeBudget.start(Duration.ofMillis(100));
        assertThat(budget.capBackoffMs(5000)).isLessThanOrEqualTo(100);
        assertThat(budget.capBackoffMs(-10)).isZero(); // 음수 백오프 방어
    }
}
