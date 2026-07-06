package com.careertuner.admin.settings.dto;

import java.util.List;

/** import 적용 결과 리포트(섹션별 적용/스킵 건수 + 사유). */
public record SettingsImportResult(
        List<Section> sections,
        int totalApplied,
        int totalSkipped
) {
    public record Section(
            String section,
            int applied,
            int skipped,
            List<String> messages
    ) {}
}
