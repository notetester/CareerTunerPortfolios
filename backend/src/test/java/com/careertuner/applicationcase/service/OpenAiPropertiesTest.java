package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class OpenAiPropertiesTest {

    @Test
    void defaultTimeoutIsFiveMinutesForLargePostingExtraction() {
        OpenAiProperties properties = new OpenAiProperties();

        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(300));
    }
}
