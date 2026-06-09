package com.careertuner.dashboard.ai;

import java.util.List;

import com.careertuner.dashboard.dto.DashboardFocusResponse;
import com.careertuner.dashboard.dto.DashboardSkillGapResponse;
import com.careertuner.dashboard.dto.DashboardStatsResponse;

/**
 * 대시보드 AI 분석 요약 입력 묶음(C 담당 AI 18).
 *
 * <p>적합도/지원 현황/부족 역량 집계를 받아 홈·대시보드에서 바로 읽을 수 있는 핵심 요약을 만든다.
 */
public record DashboardInsightAiCommand(
        DashboardStatsResponse stats,
        DashboardFocusResponse focus,
        List<DashboardSkillGapResponse> skillGaps
) {
}
