package com.careertuner.analysis.ai.provider;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CareerAnalysisAiProviderPropertiesTest {

    @Test
    @DisplayName("max-tokens 가 1024 미만이면 부팅 검증에서 실패한다(truncation footgun 차단)")
    void validate_failsWhenMaxTokensTooLow() {
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        props.getOss().setMaxTokens(512);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-tokens");
    }

    @Test
    @DisplayName("기본값(1280)과 경계값(1024)에서는 통과한다")
    void validate_passesAtDefaultAndBoundary() {
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        assertThatCode(props::validate).doesNotThrowAnyException();
        props.getOss().setMaxTokens(1024);
        assertThatCode(props::validate).doesNotThrowAnyException();
    }
}
