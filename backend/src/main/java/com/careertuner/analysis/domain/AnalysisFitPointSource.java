package com.careertuner.analysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 장기 분석의 전체 적합도 이력 한 점. 재분석을 포함한 실제 시간 추이에 사용한다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisFitPointSource {

    private Long applicationCaseId;
    private Integer fitScore;
    private LocalDateTime analyzedAt;
}
