package com.careertuner.dashboard.dto;

public record DashboardTodoResponse(
        boolean done,
        String task,
        String time
) {
}
