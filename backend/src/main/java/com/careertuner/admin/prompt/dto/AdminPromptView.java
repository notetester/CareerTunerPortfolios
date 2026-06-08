package com.careertuner.admin.prompt.dto;

public record AdminPromptView(
        String feature,
        String name,
        String version,
        String purpose,
        String systemPrompt,
        String schemaSummary
) {
}
