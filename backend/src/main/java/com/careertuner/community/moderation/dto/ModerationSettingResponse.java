package com.careertuner.community.moderation.dto;

import java.time.LocalDateTime;

public record ModerationSettingResponse(
        String strictness,
        double hideThreshold,
        int sanctionThreshold,
        int blockDays,
        int reportBlurThreshold,
        int postRateWindowSeconds,
        int postRateMax,
        int commentRateWindowSeconds,
        int commentRateMax,
        int inquiryRateWindowSeconds,
        int inquiryRateMax,
        LocalDateTime updatedAt
) {}
