package com.careertuner.admin.analytics.domain;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDailyUsageSource {

    private LocalDate date;
    private int tokenUsage;
    private int creditUsed;
}
