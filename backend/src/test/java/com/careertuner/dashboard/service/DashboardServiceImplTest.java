package com.careertuner.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.dto.CareerAnalysisRunResponse;
import com.careertuner.analysis.service.CareerAnalysisRunService;
import com.careertuner.dashboard.ai.DashboardInsightAiResult;
import com.careertuner.dashboard.ai.DashboardInsightAiService;
import com.careertuner.dashboard.domain.DashboardApplicationSource;
import com.careertuner.dashboard.domain.DashboardUserSource;
import com.careertuner.dashboard.domain.DashboardWeeklyMetricSource;
import com.careertuner.dashboard.mapper.DashboardMapper;
import tools.jackson.databind.ObjectMapper;

class DashboardServiceImplTest {

    @Test
    void excludesClosedApplicationsFromFocusAndActionTodos() {
        DashboardMapper mapper = mock(DashboardMapper.class);
        DashboardInsightAiService aiService = mock(DashboardInsightAiService.class);
        CareerAnalysisRunService runService = mock(CareerAnalysisRunService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        DashboardServiceImpl service = new DashboardServiceImpl(mapper, aiService, runService, objectMapper);

        DashboardApplicationSource closed = DashboardApplicationSource.builder()
                .applicationCaseId(1L)
                .companyName("종료기업")
                .jobTitle("백엔드 개발자")
                .status("CLOSED")
                .fitScore(99)
                .interviewCount(0)
                .build();
        DashboardApplicationSource ready = DashboardApplicationSource.builder()
                .applicationCaseId(2L)
                .companyName("지원기업")
                .jobTitle("백엔드 개발자")
                .status("READY")
                .fitScore(75)
                .interviewCount(0)
                .build();

        when(mapper.findUserById(1L)).thenReturn(DashboardUserSource.builder()
                .id(1L)
                .name("테스터")
                .plan("FREE")
                .credit(10)
                .build());
        when(mapper.findApplicationsByUserId(1L)).thenReturn(List.of(closed, ready));
        when(mapper.findTodosByUserId(1L)).thenReturn(List.of());
        when(mapper.findRecentActivitiesByUserId(1L)).thenReturn(List.of());
        when(mapper.findRecentScoredInterviews(1L)).thenReturn(List.of());
        when(mapper.findRecentNotifications(1L)).thenReturn(List.of());
        when(mapper.findFitScoreHistoryByUserId(1L)).thenReturn(List.of());
        DashboardWeeklyMetricSource weekly = new DashboardWeeklyMetricSource();
        weekly.setCurrentFitAverage(77);
        weekly.setPreviousFitAverage(70);
        weekly.setCurrentGapCount(3);
        weekly.setPreviousGapCount(5);
        weekly.setCurrentInterviewAverage(81);
        weekly.setPreviousInterviewAverage(75);
        when(mapper.findWeeklyMetricsByUserId(1L)).thenReturn(weekly);
        when(runService.findFreshRun(eq(1L), anyString(), anyString())).thenReturn(Optional.empty());
        when(aiService.summarize(any())).thenReturn(new DashboardInsightAiResult(
                "요약", CareerAnalysisAiUsage.mockUsage(), "SUCCESS", null, false));
        when(runService.record(eq(1L), anyString(), anyString(), any(), any(), any(), anyString(), any(), anyBoolean(), anyInt()))
                .thenReturn(new CareerAnalysisRunResponse(
                        1L, "DASHBOARD_SUMMARY", "SUCCESS", "{}", "{}", "mock", "v0.2", 0, null, false,
                        LocalDateTime.of(2026, 6, 1, 10, 0)));

        var summary = service.getSummary(1L);

        assertThat(summary.focus().headline()).contains("지원기업").doesNotContain("종료기업");
        assertThat(summary.todos()).extracting(todo -> todo.task())
                .anyMatch(task -> task.contains("지원기업 모의면접"))
                .anyMatch(task -> task.contains("지원기업 지원 서류 최종 점검"))
                .noneMatch(task -> task.contains("종료기업"));
        assertThat(summary.recentChange().weeklyFitScoreDelta()).isEqualTo(7);
        assertThat(summary.recentChange().weeklyGapCountDelta()).isEqualTo(-2);
        assertThat(summary.recentChange().weeklyInterviewScoreDelta()).isEqualTo(6);
    }
}
