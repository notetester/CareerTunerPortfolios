package com.careertuner.admin.reward.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 관리자 리워드 이력 목록 행(users 조인). */
@Data
public class AdminRewardHistoryRow {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
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
