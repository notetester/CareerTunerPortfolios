package com.careertuner.ad.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdCampaignRequest(
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
        Integer priority,
        Boolean active) {
}
