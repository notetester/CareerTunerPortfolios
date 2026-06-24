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
    }
}
