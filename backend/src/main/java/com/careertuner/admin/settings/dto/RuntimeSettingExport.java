package com.careertuner.admin.settings.dto;

/** 런타임 설정 1건의 export 형태(id·시각 등 휘발 필드 제외, key 기준 upsert). */
public record RuntimeSettingExport(
        String settingKey,
        String settingGroup,
        String displayName,
        String settingValue,
        String fallbackValue,
        String valueType,
        boolean secret,
        boolean editable,
        boolean active,
        String description
) {}
