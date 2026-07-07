package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.jobposting.mapper.AiRuntimeSettingMapper;

import tools.jackson.databind.ObjectMapper;

class JobPostingUploadLimitPolicyTest {

    private static final long MB = 1024L * 1024L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 인메모리 AiRuntimeSettingMapper 스텁(단일 key 저장). */
    private static final class StubSettingMapper implements AiRuntimeSettingMapper {
        private String stored;

        @Override
        public String findValueJson(String settingKey) {
            return stored;
        }

        @Override
        public int upsertValueJson(String settingKey, String valueJson, Long updatedBy) {
            this.stored = valueJson;
            return 1;
        }
    }

    private JobPostingUploadProperties properties() {
        return new JobPostingUploadProperties(); // default maxFileSizeBytes = 10MB
    }

    @Test
    void fallsBackToPropertiesWhenNoPersistedValue() {
        JobPostingUploadLimitPolicy policy =
                new JobPostingUploadLimitPolicy(properties(), new StubSettingMapper(), objectMapper);

        JobPostingUploadLimitPolicy.UploadLimitSnapshot snapshot = policy.current();

        assertThat(snapshot.maxBytes()).isEqualTo(10 * MB);
        assertThat(snapshot.source()).isEqualTo("PROPERTIES");
        assertThat(snapshot.minBytes()).isEqualTo(1 * MB);
        assertThat(snapshot.maxAllowedBytes()).isEqualTo(20 * MB);
    }

    @Test
    void configurePersistsAndCurrentReadsItBack() {
        JobPostingUploadLimitPolicy policy =
                new JobPostingUploadLimitPolicy(properties(), new StubSettingMapper(), objectMapper);

        policy.configure(15 * MB, 1L);

        JobPostingUploadLimitPolicy.UploadLimitSnapshot snapshot = policy.current();
        assertThat(snapshot.maxBytes()).isEqualTo(15 * MB);
        assertThat(snapshot.source()).isEqualTo("DATABASE");
        assertThat(policy.currentMaxBytes()).isEqualTo(15 * MB);
    }

    @Test
    void configureRejectsBelowMinAndAboveMax() {
        JobPostingUploadLimitPolicy policy =
                new JobPostingUploadLimitPolicy(properties(), new StubSettingMapper(), objectMapper);

        assertThatThrownBy(() -> policy.configure(512 * 1024, 1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.configure(21 * MB, 1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void fromPropertiesFallbackCannotConfigure() {
        JobPostingUploadLimitPolicy policy = JobPostingUploadLimitPolicy.fromProperties(properties());

        assertThat(policy.currentMaxBytes()).isEqualTo(10 * MB);
        assertThatThrownBy(() -> policy.configure(15 * MB, 1L)).isInstanceOf(BusinessException.class);
    }
}
