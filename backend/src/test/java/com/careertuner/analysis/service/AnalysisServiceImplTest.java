package com.careertuner.analysis.service;

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

import com.careertuner.analysis.ai.CareerTrendAiResult;
import com.careertuner.analysis.ai.CareerTrendAiService;
import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.domain.AnalysisFitPointSource;
import com.careertuner.analysis.domain.AnalysisSource;
import com.careertuner.analysis.dto.CareerAnalysisRunResponse;
import com.careertuner.analysis.mapper.AnalysisMapper;
import com.careertuner.notification.service.NotificationService;
import tools.jackson.databind.ObjectMapper;

class AnalysisServiceImplTest {

    @Test
    void usesFullFitHistoryAndScoredCountsForTrendAndInterviewAverage() throws Exception {
        AnalysisMapper mapper = mock(AnalysisMapper.class);
        CareerTrendAiService aiService = mock(CareerTrendAiService.class);
        CareerAnalysisRunService runService = mock(CareerAnalysisRunService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        AnalysisServiceImpl service =
                new AnalysisServiceImpl(mapper, aiService, runService, notificationService, objectMapper);

        AnalysisSource source = AnalysisSource.builder()
                .applicationCaseId(1L)
                .companyName("테스트기업")
                .jobTitle("백엔드 개발자")
                .status("READY")
                .fitAnalysisId(11L)
                .fitScore(80)
                .matchedSkills("[\"Java\"]")
                .missingSkills("[\"AWS\"]")
                .analyzedAt(LocalDateTime.of(2026, 6, 1, 10, 0))
                .interviewCount(2)
                .scoredInterviewCount(1)
                .averageInterviewScore(80)
                .interviewAnswerCount(3)
                .scoredInterviewAnswerCount(1)
                .averageInterviewAnswerScore(70)
                .build();
        List<AnalysisFitPointSource> history = List.of(
                AnalysisFitPointSource.builder()
                        .applicationCaseId(1L)
                        .fitScore(60)
                        .analyzedAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                        .build(),
                AnalysisFitPointSource.builder()
                        .applicationCaseId(1L)
                        .fitScore(80)
                        .analyzedAt(LocalDateTime.of(2026, 6, 1, 10, 0))
                        .build());

        when(mapper.findSourcesByUserId(1L)).thenReturn(List.of(source));
        when(mapper.findFitScoreHistoryByUserId(1L)).thenReturn(history);
        when(mapper.findAnswerSourcesByUserId(1L)).thenReturn(List.of());
        when(runService.findFreshRun(eq(1L), anyString(), anyString())).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(aiService.generate(any())).thenReturn(new CareerTrendAiResult(
                "요약", List.of("추천"), CareerAnalysisAiUsage.mockUsage(), "SUCCESS", null, false));
        when(runService.record(eq(1L), anyString(), anyString(), any(), any(), any(), anyString(), any(), anyBoolean(), anyInt()))
                .thenReturn(new CareerAnalysisRunResponse(
                        1L, "CAREER_TREND", "SUCCESS", "{}", "{}", "mock", "v0.2", 0, null, false,
                        LocalDateTime.of(2026, 6, 1, 10, 0)));

        var summary = service.getSummary(1L);

        assertThat(summary.scoreHistory()).extracting(point -> point.score()).containsExactly(60, 80);
        assertThat(summary.monthlyFitTrend()).extracting(point -> point.month()).containsExactly("2026-05", "2026-06");
        assertThat(summary.interviewTrend().totalSessions()).isEqualTo(2);
        assertThat(summary.interviewTrend().averageSessionScore()).isEqualTo(80);
        assertThat(summary.interviewTrend().totalAnswers()).isEqualTo(3);
        assertThat(summary.interviewTrend().averageAnswerScore()).isEqualTo(70);
    }
}
