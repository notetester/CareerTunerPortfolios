package com.careertuner.admin.settings.dto;

import java.util.List;

public record AdminJobPostingFallbackSettingRequest(
        Boolean enabled,
        List<String> allowedStages
) {
}
