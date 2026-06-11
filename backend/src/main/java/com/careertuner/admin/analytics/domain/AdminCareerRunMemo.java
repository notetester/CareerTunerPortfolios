package com.careertuner.admin.analytics.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 장기 경향/대시보드 요약 실행 이력(career_analysis_run)에 대한 관리자 운영 메모(C 담당).
 * 적합도 운영 메모(AdminFitAnalysisMemo)와 동일 구조를 실행 이력 단위로 적용한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCareerRunMemo {

    private Long id;
    private Long careerAnalysisRunId;
    private Long adminUserId;
    private String adminName;
    private String adminEmail;
    private String memoType;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
