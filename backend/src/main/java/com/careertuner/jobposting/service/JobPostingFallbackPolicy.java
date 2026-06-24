package com.careertuner.jobposting.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.service.OpenAiProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.mapper.AiRuntimeSettingMapper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobPostingFallbackPolicy {

    public static final String STAGE_PDF_OCR = "JOB_POSTING_PDF_OCR";
    public static final String STAGE_IMAGE_OCR = "JOB_POSTING_IMAGE_OCR";
    public static final List<String> AVAILABLE_STAGES = List.of(STAGE_PDF_OCR, STAGE_IMAGE_OCR);

    private static final String SETTING_KEY = "JOB_POSTING_OPENAI_FALLBACK";

    private final OpenAiProperties properties;
    private final AiRuntimeSettingMapper settingMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public JobPostingFallbackPolicy(OpenAiProperties properties,
                                    AiRuntimeSettingMapper settingMapper,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.settingMapper = settingMapper;
        this.objectMapper = objectMapper;
    }

    private JobPostingFallbackPolicy(OpenAiProperties properties) {
        this.properties = properties;
        this.settingMapper = null;
        this.objectMapper = null;
    }

    static JobPostingFallbackPolicy fromProperties(OpenAiProperties properties) {
        return new JobPostingFallbackPolicy(properties);
    }

    @Transactional(readOnly = true)
    public boolean allowed(String stage) {
        String normalizedStage = normalizeStage(stage);
        FallbackSettingSnapshot snapshot = current();
        return normalizedStage != null && snapshot.enabled() && snapshot.allowedStages().contains(normalizedStage);
    }

    @Transactional(readOnly = true)
    public FallbackSettingSnapshot current() {
        FallbackSettingSnapshot persisted = readPersisted();
        return persisted == null ? fromPropertiesSnapshot() : persisted;
    }

    @Transactional
    public FallbackSettingSnapshot configure(boolean enabled, Collection<String> allowedStages, Long updatedBy) {
        if (settingMapper == null || objectMapper == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Runtime fallback settings are not writable.");
        }
        FallbackSettingSnapshot snapshot = new FallbackSettingSnapshot(
                enabled,
                normalizeAllowedStages(allowedStages),
                AVAILABLE_STAGES,
                "DATABASE");
        try {
            settingMapper.upsertValueJson(SETTING_KEY, objectMapper.writeValueAsString(snapshot), updatedBy);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to serialize fallback setting.");
        }
        return snapshot;
    }

    private FallbackSettingSnapshot readPersisted() {
        if (settingMapper == null || objectMapper == null) {
            return null;
        }
        String valueJson = settingMapper.findValueJson(SETTING_KEY);
        if (valueJson == null || valueJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(valueJson);
            boolean enabled = root.path("enabled").asBoolean(false);
            Set<String> allowedStages = new LinkedHashSet<>();
            JsonNode stages = root.path("allowedStages");
            if (stages.isArray()) {
                for (JsonNode stage : stages) {
                    String normalized = normalizeStage(stage.asText(""));
                    if (normalized != null) {
                        allowedStages.add(normalized);
                    }
                }
            }
            return new FallbackSettingSnapshot(enabled, List.copyOf(allowedStages), AVAILABLE_STAGES, "DATABASE");
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Stored fallback setting is invalid.");
        }
    }

    private FallbackSettingSnapshot fromPropertiesSnapshot() {
        if (properties == null || !properties.isJobPostingFallbackEnabled()) {
            return new FallbackSettingSnapshot(false, List.of(), AVAILABLE_STAGES, "DEFAULT");
        }
        return new FallbackSettingSnapshot(
                true,
                normalizeAllowedStages(properties.getJobPostingFallbackAllowlist()),
                AVAILABLE_STAGES,
                "PROPERTIES");
    }

    private static List<String> normalizeAllowedStages(Collection<String> stages) {
        if (stages == null || stages.isEmpty()) {
            return List.of();
        }
        Set<String> normalizedStages = new LinkedHashSet<>();
        for (String stage : stages) {
            String normalized = normalizeStage(stage);
            if (normalized == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "fallback stage is not allowed.");
            }
            normalizedStages.add(normalized);
        }
        return List.copyOf(normalizedStages);
    }

    private static String normalizeStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return null;
        }
        String normalized = stage.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return AVAILABLE_STAGES.contains(normalized) ? normalized : null;
    }

    public record FallbackSettingSnapshot(
            boolean enabled,
            List<String> allowedStages,
            List<String> availableStages,
            String source
    ) {
        public FallbackSettingSnapshot {
            allowedStages = allowedStages == null ? List.of() : List.copyOf(allowedStages);
            availableStages = availableStages == null ? List.of() : List.copyOf(availableStages);
        }
    }
}
