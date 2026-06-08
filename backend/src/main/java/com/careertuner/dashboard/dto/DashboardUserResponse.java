package com.careertuner.dashboard.dto;

import com.careertuner.dashboard.domain.DashboardUserSource;

public record DashboardUserResponse(
        String name,
        String plan,
        int credit
) {

    public static DashboardUserResponse from(DashboardUserSource source) {
        return new DashboardUserResponse(source.getName(), source.getPlan(), source.getCredit());
    }
}
