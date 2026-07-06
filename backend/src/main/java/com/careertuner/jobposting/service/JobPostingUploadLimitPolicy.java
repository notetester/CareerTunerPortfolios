package com.careertuner.jobposting.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.mapper.AiRuntimeSettingMapper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 공고 업로드 파일 크기 한도의 런타임 정책(관리자 페이지에서 조정 가능 · {@link JobPostingFallbackPolicy} 패턴 미러).
 *
 * <p>영속값({@code ai_runtime_setting} 재사용, key={@value #SETTING_KEY})이 있으면 그 값을, 없으면
 * {@link JobPostingUploadProperties} 기본값(env/기본 10MB)을 폴백으로 쓴다. 전용 DB 스키마 변경은 없다.
 * 관리자 설정값은 Spring {@code multipart.max-file-size} 와 정합하도록 {@value #MIN_BYTES}~{@value #MAX_BYTES}
 * 범위로 제한한다(그 상한을 넘는 값은 저장하지 않고, properties 폴백도 이 범위로 clamp).
 */
@Service
public class JobPostingUploadLimitPolicy {

    private static final String SETTING_KEY = "JOB_POSTING_MAX_UPLOAD_BYTES";
    private static final long BYTES_PER_MB = 1024L * 1024L;
    static final long MIN_BYTES = 1L * BYTES_PER_MB;    // 1MB
    static final long MAX_BYTES = 20L * BYTES_PER_MB;   // 20MB — spring.servlet.multipart.max-file-size 와 정합

    private final JobPostingUploadProperties properties;
    private final AiRuntimeSettingMapper settingMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public JobPostingUploadLimitPolicy(JobPostingUploadProperties properties,
                                       AiRuntimeSettingMapper settingMapper,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.settingMapper = settingMapper;
        this.objectMapper = objectMapper;
    }

    private JobPostingUploadLimitPolicy(JobPostingUploadProperties properties) {
        this.properties = properties;
        this.settingMapper = null;
        this.objectMapper = null;
    }

    /** 영속 저장소 없이 properties 폴백만 쓰는 인스턴스(테스트/부트스트랩용). configure 는 불가. */
    static JobPostingUploadLimitPolicy fromProperties(JobPostingUploadProperties properties) {
        return new JobPostingUploadLimitPolicy(properties);
    }

    /** 업로드 검증에서 쓰는 현재 최대 바이트(관리자 설정값 또는 properties 폴백). */
    @Transactional(readOnly = true)
    public long currentMaxBytes() {
        return current().maxBytes();
    }

    @Transactional(readOnly = true)
    public UploadLimitSnapshot current() {
        Long persisted = readPersistedBytes();
        if (persisted != null) {
            return new UploadLimitSnapshot(persisted, MIN_BYTES, MAX_BYTES, "DATABASE");
        }
        return new UploadLimitSnapshot(clampToRange(properties.getMaxFileSizeBytes()), MIN_BYTES, MAX_BYTES, "PROPERTIES");
    }

    @Transactional
    public UploadLimitSnapshot configure(long maxBytes, Long updatedBy) {
        if (settingMapper == null || objectMapper == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Runtime upload limit is not writable.");
        }
        if (maxBytes < MIN_BYTES || maxBytes > MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "업로드 한도는 %dMB 이상 %dMB 이하여야 합니다.".formatted(MIN_BYTES / BYTES_PER_MB, MAX_BYTES / BYTES_PER_MB));
        }
        try {
            settingMapper.upsertValueJson(SETTING_KEY,
                    objectMapper.writeValueAsString(Map.of("maxBytes", maxBytes)), updatedBy);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to serialize upload limit setting.");
        }
        return new UploadLimitSnapshot(maxBytes, MIN_BYTES, MAX_BYTES, "DATABASE");
    }

    private Long readPersistedBytes() {
        if (settingMapper == null || objectMapper == null) {
            return null;
        }
        String valueJson = settingMapper.findValueJson(SETTING_KEY);
        if (valueJson == null || valueJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(valueJson).path("maxBytes");
            if (!node.isNumber()) {
                return null;
            }
            long bytes = node.asLong();
            return (bytes < MIN_BYTES || bytes > MAX_BYTES) ? null : bytes;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Stored upload limit setting is invalid.");
        }
    }

    private static long clampToRange(long bytes) {
        return Math.max(MIN_BYTES, Math.min(MAX_BYTES, bytes));
    }

    public record UploadLimitSnapshot(long maxBytes, long minBytes, long maxAllowedBytes, String source) {
    }
}
