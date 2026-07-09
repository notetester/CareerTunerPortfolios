package com.careertuner.planner.dto;

import java.util.List;

public record PlannerDashboardResponse(
        List<PlannerMemoResponse> memos,
        List<PlannerScheduleItemResponse> scheduleItems
) {
}
