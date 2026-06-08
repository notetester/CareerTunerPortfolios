package com.careertuner.admin.prompt.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminAnalyticsPromptResponse(
        String key,
        String name,
        String version,
        String status,
        String purpose,
        List<String> inputFields,
        List<String> outputFields,
        List<String> qualityChecklist,
        List<String> riskNotes,
        LocalDate lastReviewedAt
) {
}
