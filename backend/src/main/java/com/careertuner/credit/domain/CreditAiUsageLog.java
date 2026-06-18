package com.careertuner.credit.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 크레딧 차감 기준으로 사용할 AI 사용량 로그 조회 모델. */
@Getter
@Setter
public class CreditAiUsageLog {

    private Long id;
    private Long userId;
    private Long applicationCaseId;
    private String featureType;
    private String status;
    private Integer creditUsed;
    private LocalDateTime createdAt;

    public int creditUsedValue() {
        return creditUsed == null ? 0 : creditUsed;
    }
}
