package com.careertuner.analysis.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CareerAnalysisOssClientTest {

    private static long deadlineAfter(Duration budget) {
        return System.nanoTime() + budget.toNanos();
    }

    @Test
    @DisplayName("일시적 실패는 예산 안에서 재시도해 성공으로 회복한다")
    void withRetryWithinBudget_retriesTransientThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = CareerAnalysisOssClient.withRetryWithinBudget(3, 0, deadlineAfter(Duration.ofSeconds(5)),
                remaining -> {
                    if (calls.incrementAndGet() < 3) {
                        throw new CareerAnalysisOssClient.OssTransientException("transient");
                    }
                    return "ok";
                });
        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("재시도를 모두 소진하면 마지막 일시적 예외를 던진다")
    void withRetryWithinBudget_throwsAfterExhausting() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> CareerAnalysisOssClient.withRetryWithinBudget(2, 0, deadlineAfter(Duration.ofSeconds(5)),
                remaining -> {
                    calls.incrementAndGet();
                    throw new CareerAnalysisOssClient.OssTransientException("always");
                })).isInstanceOf(CareerAnalysisOssClient.OssTransientException.class);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("일시적이 아닌 예외(4xx 등)는 재시도하지 않고 즉시 전파한다")
    void withRetryWithinBudget_doesNotRetryNonTransient() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> CareerAnalysisOssClient.withRetryWithinBudget(3, 0, deadlineAfter(Duration.ofSeconds(5)),
                remaining -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("fatal");
                })).isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공하면 한 번만 호출한다")
    void withRetryWithinBudget_callsOnceOnSuccess() {
        AtomicInteger calls = new AtomicInteger();
        String result = CareerAnalysisOssClient.withRetryWithinBudget(3, 0, deadlineAfter(Duration.ofSeconds(5)),
                remaining -> {
                    calls.incrementAndGet();
                    return "ok";
                });
        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("총 시간예산이 소진되면 남은 재시도가 있어도 중단한다(E totalTimeBudget 패턴)")
    void withRetryWithinBudget_stopsWhenBudgetExhausted() {
        AtomicInteger calls = new AtomicInteger();
        // 예산 60ms, 시도마다 ~50ms 소모 → 2번째 시도 전(또는 중) 예산 소진 → 3회 미만으로 중단.
        assertThatThrownBy(() -> CareerAnalysisOssClient.withRetryWithinBudget(5, 0, deadlineAfter(Duration.ofMillis(60)),
                remaining -> {
                    calls.incrementAndGet();
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    throw new CareerAnalysisOssClient.OssTransientException("slow transient");
                })).isInstanceOf(CareerAnalysisOssClient.OssTransientException.class);
        assertThat(calls.get()).isLessThan(5);
    }

    @Test
    @DisplayName("attempt 콜백은 남은 예산을 받아 per-attempt 타임아웃 절삭에 쓸 수 있다")
    void withRetryWithinBudget_passesRemainingBudget() {
        AtomicReference<Duration> seen = new AtomicReference<>();
        CareerAnalysisOssClient.withRetryWithinBudget(1, 0, deadlineAfter(Duration.ofSeconds(2)), remaining -> {
            seen.set(remaining);
            return "ok";
        });
        assertThat(seen.get()).isNotNull();
        assertThat(seen.get()).isLessThanOrEqualTo(Duration.ofSeconds(2));
        assertThat(seen.get()).isGreaterThan(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("extractJsonSpan 은 앞뒤 잡설을 제거하고 JSON span 만 취한다")
    void extractJsonSpan_stripsSurroundingNoise() {
        assertThat(CareerAnalysisOssClient.extractJsonSpan("설명입니다: {\"fitSummary\":\"x\"} 끝"))
                .isEqualTo("{\"fitSummary\":\"x\"}");
        assertThat(CareerAnalysisOssClient.extractJsonSpan("[1,2,3]")).isEqualTo("[1,2,3]");
        assertThat(CareerAnalysisOssClient.extractJsonSpan("{\"a\":{\"b\":1}}")).isEqualTo("{\"a\":{\"b\":1}}");
        // JSON 이 없으면 원문을 그대로 돌려준다(상위에서 파싱 실패 처리).
        assertThat(CareerAnalysisOssClient.extractJsonSpan("no json here")).isEqualTo("no json here");
    }
}
