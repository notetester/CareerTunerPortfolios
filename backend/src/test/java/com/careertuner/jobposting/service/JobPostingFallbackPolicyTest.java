package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.applicationcase.service.OpenAiProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.jobposting.mapper.AiRuntimeSettingMapper;

import tools.jackson.databind.ObjectMapper;

class JobPostingFallbackPolicyTest {

    @Test
    void fallbackIsDisabledByDefaultWhenNoRuntimeSettingExists() {
        AiRuntimeSettingMapper mapper = mock(AiRuntimeSettingMapper.class);
        JobPostingFallbackPolicy policy = new JobPostingFallbackPolicy(
                new OpenAiProperties(),
                mapper,
                new ObjectMapper());

        assertThat(policy.current().enabled()).isFalse();
        assertThat(policy.current().allowedStages()).isEmpty();
        assertThat(policy.allowed(JobPostingFallbackPolicy.STAGE_IMAGE_OCR)).isFalse();
    }

    @Test
    void persistedSettingOverridesPropertyAllowlist() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setJobPostingFallbackEnabled(false);
        AiRuntimeSettingMapper mapper = mock(AiRuntimeSettingMapper.class);
        when(mapper.findValueJson("JOB_POSTING_OPENAI_FALLBACK")).thenReturn("""
                {
                  "enabled": true,
                  "allowedStages": ["JOB_POSTING_IMAGE_OCR"]
                }
                """);
        JobPostingFallbackPolicy policy = new JobPostingFallbackPolicy(properties, mapper, new ObjectMapper());

        assertThat(policy.allowed(JobPostingFallbackPolicy.STAGE_IMAGE_OCR)).isTrue();
        assertThat(policy.allowed(JobPostingFallbackPolicy.STAGE_PDF_OCR)).isFalse();
        assertThat(policy.current().source()).isEqualTo("DATABASE");
    }

    @Test
    void configureStoresNormalizedAllowlistAsJson() {
        AiRuntimeSettingMapper mapper = mock(AiRuntimeSettingMapper.class);
        JobPostingFallbackPolicy policy = new JobPostingFallbackPolicy(
                new OpenAiProperties(),
                mapper,
                new ObjectMapper());

        JobPostingFallbackPolicy.FallbackSettingSnapshot snapshot = policy.configure(
                true,
                List.of("job-posting-image-ocr", "JOB_POSTING_PDF_OCR", "JOB_POSTING_IMAGE_OCR"),
                7L);

        assertThat(snapshot.enabled()).isTrue();
        assertThat(snapshot.allowedStages()).containsExactly(
                JobPostingFallbackPolicy.STAGE_IMAGE_OCR,
                JobPostingFallbackPolicy.STAGE_PDF_OCR);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertValueJson(eq("JOB_POSTING_OPENAI_FALLBACK"), jsonCaptor.capture(), eq(7L));
        assertThat(jsonCaptor.getValue()).contains("\"enabled\":true");
        assertThat(jsonCaptor.getValue()).contains("JOB_POSTING_IMAGE_OCR");
        assertThat(jsonCaptor.getValue()).contains("JOB_POSTING_PDF_OCR");
    }

    @Test
    void configureRejectsUnknownStage() {
        JobPostingFallbackPolicy policy = new JobPostingFallbackPolicy(
                new OpenAiProperties(),
                mock(AiRuntimeSettingMapper.class),
                new ObjectMapper());

        assertThatThrownBy(() -> policy.configure(true, List.of("JOB_POSTING_METADATA"), 7L))
                .isInstanceOf(BusinessException.class);
    }
}
