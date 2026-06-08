package com.careertuner.admin.analytics.dto;

import com.careertuner.admin.analytics.domain.AdminCountSource;

public record AdminCountResponse(
        String label,
        int count
) {

    public static AdminCountResponse from(AdminCountSource source) {
        return new AdminCountResponse(source.getLabel(), source.getCount());
    }
}
