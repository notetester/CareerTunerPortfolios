package com.careertuner.community.moderation.dto;

import java.util.List;

public record ModerationStatsResponse(
        List<CategoryCount> categories,
        int total
) {
    public record CategoryCount(String category, int count) {}
}
