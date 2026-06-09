package com.careertuner.analysis.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.dto.AnalysisScorePointResponse;
import com.careertuner.analysis.dto.AnalysisStatResponse;
import com.careertuner.analysis.dto.JobReadinessResponse;
import com.careertuner.analysis.dto.InterviewTrendResponse;
import com.careertuner.analysis.dto.SkillGapResponse;

/**
 * 장기 취업 경향/다음 지원 방향 AI의 mock 구현(API 키 미발급 단계 기본 동작).
 *
 * <p>누적 집계만으로 결정적 요약/추천을 만든다. 실 LLM 연동 전까지 화면과 관리자 통계 흐름을 그대로 검증할 수 있다.
 */
@Service
public class MockCareerTrendAiService implements CareerTrendAiService {

    @Override
    public CareerTrendAiResult generate(CareerTrendAiCommand command) {
        return new CareerTrendAiResult(
                trendSummary(command),
                recommendedDirections(command),
                CareerAnalysisAiUsage.mockUsage(),
                "SUCCESS",
                null,
                false);
    }

    private String trendSummary(CareerTrendAiCommand command) {
        AnalysisStatResponse stats = command.stats();
        if (stats == null || stats.analyzedApplications() == 0) {
            return "아직 분석된 지원 건이 적어 장기 경향을 단정하기 어렵습니다. 지원 건을 등록하고 적합도 분석을 실행하면 반복 부족 역량과 직무 패턴이 누적되어 표시됩니다.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("전체 %d건 중 %d건을 분석했고 평균 적합도는 %d점입니다. "
                .formatted(stats.totalApplications(), stats.analyzedApplications(), stats.averageFitScore()));

        List<SkillGapResponse> gaps = safe(command.skillGaps());
        if (!gaps.isEmpty()) {
            SkillGapResponse top = gaps.get(0);
            sb.append("반복적으로 부족한 역량은 %s(으)로 분석 %d건 중 %d건에서 나타났습니다. "
                    .formatted(top.skill(), top.total(), top.count()));
        }

        List<JobReadinessResponse> readiness = safe(command.jobReadiness());
        if (!readiness.isEmpty()) {
            JobReadinessResponse strong = readiness.get(0);
            sb.append("%s 직무 준비도가 %d점으로 가장 높습니다. ".formatted(strong.jobTitle(), strong.readiness()));
        }

        List<AnalysisScorePointResponse> history = safe(command.scoreHistory());
        if (history.size() >= 2) {
            int first = nullToZero(history.get(0).score());
            int last = nullToZero(history.get(history.size() - 1).score());
            int delta = last - first;
            sb.append("적합도 점수는 %d회 분석 기준 %s%d점 변화했습니다(%d→%d)."
                    .formatted(history.size(), delta >= 0 ? "+" : "", delta, first, last));
        }
        InterviewTrendResponse interview = command.interviewTrend();
        if (interview != null && interview.totalSessions() > 0) {
            sb.append(" 누적 모의면접은 %d회이며 평균 점수는 %d점입니다."
                    .formatted(interview.totalSessions(), interview.averageSessionScore()));
        }
        return sb.toString().trim();
    }

    private List<String> recommendedDirections(CareerTrendAiCommand command) {
        List<String> directions = new ArrayList<>();
        safe(command.skillGaps()).stream()
                .limit(3)
                .forEach(gap -> directions.add("%s 보완을 우선 과제로 잡으세요. 최근 분석 %d건 중 %d건에서 부족 역량으로 나타났습니다."
                        .formatted(gap.skill(), gap.total(), gap.count())));

        safe(command.jobReadiness()).stream()
                .findFirst()
                .ifPresent(job -> directions.add("%s 직무는 준비도가 %d점으로 높아 우선 지원 후보입니다."
                        .formatted(job.jobTitle(), job.readiness())));

        if (command.bestStrategy() != null && !command.bestStrategy().isBlank()) {
            directions.add("가장 적합도가 높은 지원 건 전략: " + command.bestStrategy().trim());
        }
        if (command.interviewTrend() == null || command.interviewTrend().totalSessions() == 0) {
            directions.add("관심 직무 지원 건으로 모의면접을 진행해 장기 경향 분석에 면접 데이터를 추가하세요.");
        }

        if (directions.isEmpty()) {
            directions.add("분석 결과가 쌓이면 반복 부족 역량과 다음 지원 방향을 추천합니다.");
        }
        return directions.stream().limit(5).toList();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
