package com.careertuner.admin.settings.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 관리자 설정 export/import 봉투. 요청한 섹션만 채워지고 나머지는 null(직렬화 시 생략).
 * TripTogether {@code InitialSettingsService} 의 섹션 export/import 를 이식했다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SettingsExport(
        int schemaVersion,
        String exportedAt,
        List<RuntimeSettingExport> runtimeSettings,
        ModerationExport moderation
) {
    public static final int SCHEMA_VERSION = 1;
}
