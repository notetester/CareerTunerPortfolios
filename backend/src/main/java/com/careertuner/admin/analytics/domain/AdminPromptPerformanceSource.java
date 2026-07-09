package com.careertuner.admin.analytics.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPromptPerformanceSource {
    private String promptKey;
    private String promptVersion;
    private int totalCount;
    private int successCount;
    private int fallbackCount;
    private int failedCount;
    private int averageTokenUsage;
}
