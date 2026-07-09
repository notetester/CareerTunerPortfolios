package com.careertuner.reward.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 리워드 적립/레벨업/쿠폰 발급 감사 이력(user_reward_history). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRewardHistory {
    private Long id;
    private Long userId;
    private String eventCode;
    private int pointDelta;
    private int creditDelta;
    private Integer levelBefore;
    private Integer levelAfter;
    private String refType;
    private Long refId;
    private String reason;
    private LocalDateTime createdAt;
}
