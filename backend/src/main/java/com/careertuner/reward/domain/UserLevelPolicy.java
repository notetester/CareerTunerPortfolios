package com.careertuner.reward.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 활동 레벨 임계 및 레벨업 보상 정책(user_level_policy). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLevelPolicy {
    private Long id;
    private int level;
    private String levelName;
    private int minPoint;
    private int levelupCredit;
    private String levelupCouponCode;
    private String benefitNote;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
