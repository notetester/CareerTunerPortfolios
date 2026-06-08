package com.careertuner.analysis.ai;

import java.util.List;

import com.careertuner.analysis.dto.AnalysisScorePointResponse;
import com.careertuner.analysis.dto.AnalysisStatResponse;
import com.careertuner.analysis.dto.JobReadinessResponse;
import com.careertuner.analysis.dto.SkillGapResponse;

/**
 * 장기 취업 경향/다음 지원 방향 AI 입력 묶음(C 담당 AI 16~17).
 *
 * <p>여러 지원 건의 누적 집계(적합도 통계, 반복 부족 역량, 직무 준비도, 점수 추이)를 받는다.
 * 개별 원본 데이터는 각 담당 영역이 관리하며, 여기서는 집계 결과만 사용한다.
 */
public record CareerTrendAiCommand(
        AnalysisStatResponse stats,
        List<SkillGapResponse> skillGaps,
        List<JobReadinessResponse> jobReadiness,
        List<AnalysisScorePointResponse> scoreHistory,
        String bestStrategy
) {
}
