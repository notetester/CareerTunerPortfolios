package com.careertuner.dashboard.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 최근 변화 요약용 적합도 분석 이력 한 점(fit_analysis 읽기 전용, C 소유). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardFitPointSource {

    private Long applicationCaseId;
    private Integer fitScore;
    private LocalDateTime createdAt;
}
