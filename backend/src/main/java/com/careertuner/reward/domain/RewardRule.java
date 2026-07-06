package com.careertuner.reward.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 활동 이벤트별 리워드 적립 규칙(reward_rule). 관리자가 값/사용여부를 조정한다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardRule {
    private Long id;
    private String eventCode;
    private String name;
    private int pointAmount;
    private int creditAmount;
    private Integer dailyCap;
    private boolean enabled;
    private String description;
    private int sortOrder;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
