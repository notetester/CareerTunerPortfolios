package com.careertuner.analysis.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;

class CareerAnalysisOssClientTest {

    /** 기본 per-attempt 타임아웃(테스트 고정값 — 예산 절삭 대상). */
    private static final Duration PER_ATTEMPT = Duration.ofSeconds(1);

    @Test
    @DisplayName("일시적 실패는 예산 안에서 재시도해 성공으로 회복한다")
    void withRetryWithinBudget_retriesTransientThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = CareerAnalysisOssClient.withRetryWithinBudget(3, 0, PER_ATTEMPT,
                AiTotalTimeBudget.start(Duration.ofSeconds(5)),
                timeout -> {
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
        assertThatThrownBy(() -> CareerAnalysisOssClient.withRetryWithinBudget(2, 0, PER_ATTEMPT,
                AiTotalTimeBudget.start(Duration.ofSeconds(5)),
                timeout -> {
                    calls.incrementAndGet();
                    throw new CareerAnalysisOssClient.OssTransientException("always");
                })).isInstanceOf(CareerAnalysisOssClient.OssTransientException.class);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("일시적이 아닌 예외(4xx 등)는 재시도하지 않고 즉시 전파한다")
    void withRetryWithinBudget_doesNotRetryNonTransient() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> CareerAnalysisOssClient.withRetryWithinBudget(3, 0, PER_ATTEMPT,
                AiTotalTimeBudget.start(Duration.ofSeconds(5)),
                timeout -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("fatal");
                })).isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공하면 한 번만 호출한다")
    void withRetryWithinBudget_callsOnceOnSuccess() {
        AtomicInteger calls = new AtomicInteger();
        String result = CareerAnalysisOssClient.withRetryWithinBudget(3, 0, PER_ATTEMPT,
                AiTotalTimeBudget.start(Duration.ofSeconds(5)),
                timeout -> {
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
        assertThatThrownBy(() -> CareerAnalysisOssClient.withRetryWithinBudget(5, 0, PER_ATTEMPT,
                AiTotalTimeBudget.start(Duration.ofMillis(60)),
                timeout -> {
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
    @DisplayName("아주 작은 예산은 남은 시도가 있어도 소진 즉시 일시적 예외로 중단한다")
    void withRetryWithinBudget_tinyBudgetStopsRetrying() {
        AtomicInteger calls = new AtomicInteger();
        // 예산 1ms — 첫 시도(~5ms 소모)만으로 소진 → 남은 시도와 무관하게 OssTransientException 으로 중단.
        assertThatThrownBy(() -> CareerAnalysisOssClient.withRetryWithinBudget(5, 0, PER_ATTEMPT,
                AiTotalTimeBudget.start(Duration.ofMillis(1)),
                timeout -> {
                    calls.incrementAndGet();
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    throw new CareerAnalysisOssClient.OssTransientException("slow transient");
                })).isInstanceOf(CareerAnalysisOssClient.OssTransientException.class);
        assertThat(calls.get()).isLessThan(5);
    }

    @Test
    @DisplayName("예산 0(무제한)이면 예산 체크 없이 모든 재시도가 그대로 동작한다")
    void withRetryWithinBudget_zeroBudgetIsUnlimited() {
        AtomicInteger calls = new AtomicInteger();
        String result = CareerAnalysisOssClient.withRetryWithinBudget(3, 0, PER_ATTEMPT,
                AiTotalTimeBudget.start(Duration.ZERO),
                timeout -> {
                    if (calls.incrementAndGet() < 3) {
                        throw new CareerAnalysisOssClient.OssTransientException("transient");
                    }
                    return "ok";
                });
        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("attempt 콜백은 남은 예산으로 절삭된 per-attempt 타임아웃을 받는다")
    void withRetryWithinBudget_capsPerAttemptTimeout() {
        AtomicReference<Duration> seen = new AtomicReference<>();
        // per-attempt 10초 > 예산 2초 → 남은 예산(≤2초)으로 절삭돼 전달된다.
        CareerAnalysisOssClient.withRetryWithinBudget(1, 0, Duration.ofSeconds(10),
                AiTotalTimeBudget.start(Duration.ofSeconds(2)), timeout -> {
                    seen.set(timeout);
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

    @Test
    @DisplayName("repairTruncatedJson: 닫힘 괄호가 잘린 truncation 을 수리한다(post-R3 재벤치마크 실측 형태)")
    void repairTruncatedJson_repairsRealBenchmarkTruncation() {
        // EA-A-059/055/057 PARSE_FAIL 실측 형태 — 루트 닫는 중괄호 1개 누락(내용은 합성 축약)
        String truncated = """
                {
                  "gateResult": {
                    "gateStatus": "REVIEW_REQUIRED",
                    "reasons": [
                      {"skillName": "WMS", "severity": "WARNING"}
                    ]
                  }""";
        assertThat(CareerAnalysisOssClient.repairTruncatedJson(truncated))
                .isEqualTo(truncated + "}");

        // 배열 중간 절단 + trailing comma
        assertThat(CareerAnalysisOssClient.repairTruncatedJson("{\"a\": [1, 2,"))
                .isEqualTo("{\"a\": [1, 2]}");
    }

    @Test
    @DisplayName("repairTruncatedJson: truncation 이 아닌 손상은 수리하지 않는다(null)")
    void repairTruncatedJson_rejectsNonTruncationDamage() {
        assertThat(CareerAnalysisOssClient.repairTruncatedJson("{\"a\": \"cut mid strin")).isNull(); // 문자열 중간 절단
        assertThat(CareerAnalysisOssClient.repairTruncatedJson("{\"a\": 1]")).isNull();               // 괄호 불일치
        assertThat(CareerAnalysisOssClient.repairTruncatedJson("{\"a\": 1}")).isNull();               // 이미 균형(다른 원인)
        assertThat(CareerAnalysisOssClient.repairTruncatedJson("plain text")).isNull();
    }
}
