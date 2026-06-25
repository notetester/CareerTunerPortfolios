package com.careertuner.admin.settings.dto;

import java.util.List;

import com.careertuner.jobposting.service.JobPostingFallbackPolicy.FallbackSettingSnapshot;

public record AdminJobPostingFallbackSettingResponse(
        boolean enabled,
        List<String> allowedStages,
        List<String> availableStages,
        String source
) {
    public static AdminJobPostingFallbackSettingResponse from(FallbackSettingSnapshot snapshot) {
        return new AdminJobPostingFallbackSettingResponse(
                snapshot.enabled(),
                snapshot.allowedStages(),
                snapshot.availableStages(),
                snapshot.source());
    }
}
