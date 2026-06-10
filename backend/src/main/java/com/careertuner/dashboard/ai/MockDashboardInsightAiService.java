package com.careertuner.dashboard.ai;

import java.util.List;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.dashboard.dto.DashboardSkillGapResponse;
import com.careertuner.dashboard.dto.DashboardStatsResponse;

/**
 * 대시보드 AI 요약의 mock 구현(API 키 미발급 단계 기본 동작).
 *
 * <p>대시보드 집계만으로 결정적 요약을 만든다. 실 LLM 연동 전까지 홈/대시보드 화면을 그대로 검증할 수 있다.
 */
@Service
public class MockDashboardInsightAiService implements DashboardInsightAiService {

    @Override
    public DashboardInsightAiResult summarize(DashboardInsightAiCommand command) {
        DashboardStatsResponse stats = command.stats();
        if (stats == null || stats.activeApplications() == 0) {
            return new DashboardInsightAiResult(
                    "아직 진행 중인 지원 건이 없습니다. 첫 지원 건을 등록하고 공고 분석과 적합도 분석을 실행하면 준비 현황이 여기 요약됩니다.",
                    CareerAnalysisAiUsage.mockUsage(),
                    "SUCCESS",
                    null,
                    false);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("진행 중인 지원 건 %d개, 평균 적합도 %d점입니다. "
                .formatted(stats.activeApplications(), stats.averageFitScore()));

        List<DashboardSkillGapResponse> gaps = command.skillGaps();
        if (gaps != null && !gaps.isEmpty()) {
            sb.append("우선 보완할 역량은 %s입니다. ".formatted(gaps.get(0).skill()));
        }

        if (command.focus() != null && command.focus().headline() != null) {
            sb.append(command.focus().headline()).append(" ");
        }

        if (stats.interviewsThisWeek() == 0) {
            sb.append("이번 주 모의면접 기록이 없으니 1회 진행을 권장합니다.");
        } else {
            sb.append("이번 주 모의면접 %d회를 진행했습니다.".formatted(stats.interviewsThisWeek()));
        }
        return new DashboardInsightAiResult(
                sb.toString().trim(),
                CareerAnalysisAiUsage.mockUsage(),
                "SUCCESS",
                null,
                false);
    }
}
