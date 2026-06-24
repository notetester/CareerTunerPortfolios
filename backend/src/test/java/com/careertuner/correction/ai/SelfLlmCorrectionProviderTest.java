package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelfLlmCorrectionProviderTest {

    @Test
    @DisplayName("retries transient self LLM failures")
    void withRetry_retriesTransientThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = SelfLlmCorrectionProvider.withRetry(3, 0, () -> {
            if (calls.incrementAndGet() < 3) {
                throw new SelfLlmCorrectionProvider.SelfLlmTransientException("transient");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("does not retry non-transient failures")
    void withRetry_doesNotRetryNonTransient() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> SelfLlmCorrectionProvider.withRetry(3, 0, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("fatal");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
}
