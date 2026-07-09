package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class OpenAiPropertiesTest {

    @Test
    void defaultTimeoutIsFiveMinutesForLargePostingExtraction() {
        OpenAiProperties properties = new OpenAiProperties();

        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    void bareOpenAiPropertiesHaveFallbackDisabledUntilConfigured() {
        // 설정이 전혀 없는 맨 객체는 off — application.yaml 이 런타임 기본값을 켠다(아래 테스트 참조).
        OpenAiProperties properties = new OpenAiProperties();

        assertThat(properties.isJobPostingFallbackEnabled()).isFalse();
        assertThat(properties.jobPostingFallbackAllowed("JOB_POSTING_IMAGE_OCR")).isFalse();
    }

    @Test
    void jobPostingFallbackDefaultsToEnabledForBothOcrStages() {
        // application.yaml 기본값(enabled=true, allowlist=콤마구분 두 스테이지)이 Set 으로 바인딩되고
        // PDF/IMAGE OCR 두 스테이지 모두 허용되는지 검증(OpenAI 폴백 기본 ON).
        Map<String, Object> yamlDefaults = Map.of(
                "careertuner.openai.job-posting-fallback-enabled", "true",
                "careertuner.openai.job-posting-fallback-allowlist", "JOB_POSTING_PDF_OCR,JOB_POSTING_IMAGE_OCR");
        OpenAiProperties properties = new Binder(new MapConfigurationPropertySource(yamlDefaults))
                .bind("careertuner.openai", OpenAiProperties.class)
                .get();

        assertThat(properties.isJobPostingFallbackEnabled()).isTrue();
        assertThat(properties.jobPostingFallbackAllowed("JOB_POSTING_PDF_OCR")).isTrue();
        assertThat(properties.jobPostingFallbackAllowed("JOB_POSTING_IMAGE_OCR")).isTrue();
    }
}
