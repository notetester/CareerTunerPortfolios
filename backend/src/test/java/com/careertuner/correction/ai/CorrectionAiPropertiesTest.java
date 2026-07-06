package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorrectionAiPropertiesTest {

    @Test
    @DisplayName("self provider is enabled only when provider=self and base-url exists")
    void selfProviderEnabled_requiresProviderAndBaseUrl() {
        CorrectionAiProperties properties = new CorrectionAiProperties();
        assertThat(properties.selfProviderEnabled()).isFalse();

        properties.setProvider("self");
        assertThat(properties.selfProviderEnabled()).isFalse();

        properties.getSelf().setBaseUrl("http://localhost:11434");
        assertThat(properties.selfProviderEnabled()).isTrue();
        assertThat(properties.getSelf().getModel()).isEqualTo("careertuner-e-correction-3b:latest");
        assertThat(properties.getSelf().getMaxTokens()).isEqualTo(3072);
        assertThat(properties.getSelf().getMaxAttempts()).isEqualTo(2);
        assertThat(properties.getOpenAiTimeout()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(properties.getOpenAiMaxAttempts()).isEqualTo(1);
    }
}
