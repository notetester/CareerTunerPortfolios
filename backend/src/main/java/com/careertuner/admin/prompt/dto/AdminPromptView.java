package com.careertuner.admin.prompt.dto;

import java.util.List;
import java.util.Map;

public record AdminPromptView(
        String feature,
        String name,
        String version,
        String purpose,
        String systemPrompt,
        String schemaSummary,
        List<Map<String, Object>> evaluationCriteria,
        List<Map<String, Object>> weightProfiles
) {
    public AdminPromptView(String feature,
                           String name,
                           String version,
                           String purpose,
                           String systemPrompt,
                           String schemaSummary) {
        this(feature, name, version, purpose, systemPrompt, schemaSummary, List.of(), List.of());
    }
}
