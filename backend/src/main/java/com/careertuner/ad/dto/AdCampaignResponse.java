package com.careertuner.ad.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdCampaignResponse(
        Long id,
        String title,
        String body,
        String surface,
        String placement,
        String creativeType,
        String imageUrl,
        String targetUrl,
        List<String> visibleToPlans,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        int priority,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
