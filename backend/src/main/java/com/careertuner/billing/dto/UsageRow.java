package com.careertuner.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 이번 달 AI 기능별 사용량 한 줄(ai_usage_log 집계). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageRow {

    private String featureType;
    private int used;
    private int creditUsed;
}
