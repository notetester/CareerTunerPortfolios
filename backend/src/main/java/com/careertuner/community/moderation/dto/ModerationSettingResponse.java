package com.careertuner.community.moderation.dto;

import java.time.LocalDateTime;

public record ModerationSettingResponse(
        String strictness,
        double hideThreshold,
        LocalDateTime updatedAt
) {}
