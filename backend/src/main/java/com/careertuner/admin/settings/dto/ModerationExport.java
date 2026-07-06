package com.careertuner.admin.settings.dto;

/** 콘텐츠 중재 정책(단일행)의 export 형태. */
public record ModerationExport(
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
        int inquiryRateMax
) {}
