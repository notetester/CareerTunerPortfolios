package com.careertuner.analysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerGoal {
    private Long id;
    private Long userId;
    private String targetJob;
    private String targetPeriod;
    private String prioritySkill;
    private String preferredCompanyType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
