package com.careertuner.community.moderation.dto;

public record ModerationSettingUpdateRequest(
        String strictness,
        Double hideThreshold,
        Integer sanctionThreshold,
        Integer blockDays,
        // 작성 rate-limit(도배 방지) + 신고 누적 블러 임계 — 부분 갱신(null이면 기존값 유지)
        Integer reportBlurThreshold,
        Integer postRateWindowSeconds,
        Integer postRateMax,
        Integer commentRateWindowSeconds,
        Integer commentRateMax,
        Integer inquiryRateWindowSeconds,
        Integer inquiryRateMax
) {}
