package com.careertuner.runtimesetting.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.runtimesetting.domain.RuntimeSetting;
import com.careertuner.runtimesetting.domain.RuntimeSettingHistory;
import com.careertuner.runtimesetting.mapper.RuntimeSettingMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * key-value 런타임 설정 서비스.
 *
 * <p>코드가 DB 설정을 실시간 참조한다(DB 값 → fallback → 코드 기본값 순). 모든 변경은 actor·before/after·버전으로
 * 이력화한다. TripTogether {@code RuntimeSettingService} 를 이식했다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeSettingService {

    private final RuntimeSettingMapper mapper;

    /* ── 런타임 조회(코드에서 사용) ── */

    public String getValue(String settingKey, String fallbackValue) {
        try {
            RuntimeSetting setting = mapper.findActiveSettingByKey(settingKey);
            if (setting == null) {
                return fallbackValue;
            }
            if (hasText(setting.getSettingValue())) {
                return setting.getSettingValue().trim();
            }
            if (hasText(setting.getFallbackValue())) {
                return setting.getFallbackValue().trim();
            }
            return fallbackValue;
        } catch (Exception e) {
            log.debug("[RuntimeSetting] fallback used key={} reason={}", settingKey, e.getMessage());
            return fallbackValue;
        }
    }

    public int getInt(String settingKey, int fallbackValue) {
        try {
            return Integer.parseInt(getValue(settingKey, String.valueOf(fallbackValue)));
        } catch (Exception e) {
            return fallbackValue;
        }
    }

    public boolean getBoolean(String settingKey, boolean fallbackValue) {
        String value = getValue(settingKey, String.valueOf(fallbackValue));
        if (value == null) {
            return fallbackValue;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "Y".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    /* ── 관리자 콘솔 ── */

    public List<RuntimeSetting> getRuntimeSettings(String settingGroup, String keyword, boolean includeInactive) {
        return mapper.findSettings(emptyToNull(settingGroup), emptyToNull(keyword), includeInactive);
    }

    public List<RuntimeSettingHistory> getHistories(String settingKey, int limit) {
        return mapper.findHistory(emptyToNull(settingKey), Math.max(1, Math.min(limit, 200)));
    }

    /** 설정 저장(신규 CREATE 또는 UPDATE) + 변경 이력 기록. */
    @Transactional
    public RuntimeSetting saveRuntimeSetting(RuntimeSetting input, Long actorUserId) {
        RuntimeSetting before = mapper.findByKey(required(input.getSettingKey()));

        RuntimeSetting setting = RuntimeSetting.builder()
                .settingKey(input.getSettingKey().trim())
                .settingGroup(defaultText(input.getSettingGroup(), "GENERAL"))
                .displayName(defaultText(input.getDisplayName(), input.getSettingKey()))
                .settingValue(emptyToNull(input.getSettingValue()))
                .fallbackValue(emptyToNull(input.getFallbackValue()))
                .valueType(defaultText(input.getValueType(), "STRING"))
                .secret(input.isSecret())
                .editable(input.isEditable())
                .active(input.isActive())
                .description(emptyToNull(input.getDescription()))
                .updatedBy(actorUserId)
                .build();

        if (before == null) {
            mapper.insertSetting(setting);
        } else {
            mapper.updateSetting(setting);
        }
        RuntimeSetting after = mapper.findByKey(setting.getSettingKey());
        int version = mapper.nextVersionNo(setting.getSettingKey());
        mapper.insertHistory(
                after == null ? null : after.getId(),
                setting.getSettingKey(),
                version,
                before == null ? "CREATE" : "UPDATE",
                actorUserId,
                before == null ? null : before.getSettingValue(),
                setting.getSettingValue(),
                before == null ? null : before.getFallbackValue(),
                setting.getFallbackValue(),
                snapshot(before),
                snapshot(after == null ? setting : after));
        return after;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("settingKey");
        }
        return value.trim();
    }

    private static String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String emptyToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String snapshot(RuntimeSetting s) {
        if (s == null) {
            return "{}";
        }
        return "{"
                + pair("settingKey", s.getSettingKey()) + ","
                + pair("settingGroup", s.getSettingGroup()) + ","
                + pair("displayName", s.getDisplayName()) + ","
                + pair("settingValue", s.getSettingValue()) + ","
                + pair("fallbackValue", s.getFallbackValue()) + ","
                + pair("valueType", s.getValueType()) + ","
                + pair("secret", s.isSecret()) + ","
                + pair("editable", s.isEditable()) + ","
                + pair("active", s.isActive())
                + "}";
    }

    private static String pair(String key, Object value) {
        return "\"" + esc(key) + "\":\"" + esc(value == null ? "" : String.valueOf(value)) + "\"";
    }

    private static String esc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
