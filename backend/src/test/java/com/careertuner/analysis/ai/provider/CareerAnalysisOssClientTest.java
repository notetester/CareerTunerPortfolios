package com.careertuner.analysis.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CareerAnalysisOssClientTest {

    @Test
    @DisplayName("일시적 실패는 재시도해서 성공으로 회복한다")
    void withRetry_retriesTransientThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = CareerAnalysisOssClient.withRetry(3, 0, () -> {
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
    void withRetry_throwsAfterExhausting() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> CareerAnalysisOssClient.withRetry(2, 0, () -> {
            calls.incrementAndGet();
            throw new CareerAnalysisOssClient.OssTransientException("always");
        })).isInstanceOf(CareerAnalysisOssClient.OssTransientException.class);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("일시적이 아닌 예외(4xx 등)는 재시도하지 않고 즉시 전파한다")
    void withRetry_doesNotRetryNonTransient() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> CareerAnalysisOssClient.withRetry(3, 0, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("fatal");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공하면 한 번만 호출한다")
    void withRetry_callsOnceOnSuccess() {
        AtomicInteger calls = new AtomicInteger();
        String result = CareerAnalysisOssClient.withRetry(3, 0, () -> {
            calls.incrementAndGet();
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
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
