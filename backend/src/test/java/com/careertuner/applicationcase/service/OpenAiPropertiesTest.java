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

    @Test
    void jobPostingFallbackIsDisabledByDefault() {
        OpenAiProperties properties = new OpenAiProperties();

        assertThat(properties.isJobPostingFallbackEnabled()).isFalse();
        assertThat(properties.jobPostingFallbackAllowed("JOB_POSTING_IMAGE_OCR")).isFalse();
    }
}
