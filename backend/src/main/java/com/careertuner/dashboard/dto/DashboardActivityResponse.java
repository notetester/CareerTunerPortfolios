package com.careertuner.dashboard.dto;

import java.time.LocalDateTime;

import com.careertuner.dashboard.domain.DashboardActivitySource;

public record DashboardActivityResponse(
        String type,
        Long applicationCaseId,
        String content,
        LocalDateTime occurredAt,
        Integer score
) {

    public static DashboardActivityResponse from(DashboardActivitySource source) {
        return new DashboardActivityResponse(
                source.getType(),
                source.getApplicationCaseId(),
                source.getContent(),
                source.getOccurredAt(),
                source.getScore());
    }
}
