package com.careertuner.analysis.dto;

import jakarta.validation.constraints.Size;

public record CareerGoalRequest(
        @Size(max = 255) String targetJob,
        @Size(max = 100) String targetPeriod,
        @Size(max = 255) String prioritySkill,
        @Size(max = 255) String preferredCompanyType
) {
}
