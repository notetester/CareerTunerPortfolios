package com.careertuner.community.moderation.dto;

public record ModerationSettingUpdateRequest(
        String strictness,
        Double hideThreshold
) {}
