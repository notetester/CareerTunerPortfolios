package com.careertuner.admin.fitanalysis.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** gate 통계 집계용 최소 행 — fit_analysis_gate_result 단일 테이블(조인 없음), 집계는 서비스에서 수행. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminGateStatsRow {

    private String gateStatus;
    private String reviewStatus;
    private String maxSeverity;
    // 축약 gate reason 목록 JSON([{type,claim,reason,severity}]). null/'[]'/깨진 JSON 가능 — 서비스에서 방어 파싱.
    private String gateReasonsJson;
}
