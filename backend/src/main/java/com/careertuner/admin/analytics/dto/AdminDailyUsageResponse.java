package com.careertuner.admin.analytics.dto;

import java.time.LocalDate;

import com.careertuner.admin.analytics.domain.AdminDailyUsageSource;

public record AdminDailyUsageResponse(
        LocalDate date,
        int tokenUsage,
        int creditUsed
) {

    public static AdminDailyUsageResponse from(AdminDailyUsageSource source) {
        return new AdminDailyUsageResponse(source.getDate(), source.getTokenUsage(), source.getCreditUsed());
    }
}
