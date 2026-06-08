package com.careertuner.dashboard.dto;

public record DashboardFocusResponse(
        String headline,
        String description,
        Integer readiness
) {
}
