package com.careertuner.billing.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MyBenefitsResponse(
        String planCode,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        List<UserBenefitBalanceResponse> benefits
) {
}
