package com.careertuner.fitanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.fitanalysis.ai.MockFitAnalysisAiService;
import com.careertuner.fitanalysis.domain.FitAnalysisGenerationSource;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

class FitAnalysisServiceImplTest {

    @Test
    void generatePersistsHistoryAndNormalizedConditionMatches() {
        FitAnalysisMapper mapper = mock(FitAnalysisMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        FitAnalysisServiceImpl service = new FitAnalysisServiceImpl(mapper, new MockFitAnalysisAiService(), mock(NotificationService.class), objectMapper);
        FitAnalysisGenerationSource source = source();
        FitAnalysisResult previous = FitAnalysisResult.builder()
                .id(10L).applicationCaseId(20L).fitScore(60)
                .matchedSkills("[\"React\"]").missingSkills("[\"TypeScript\"]")
                .build();

        when(mapper.findGenerationSource(1L, 20L)).thenReturn(source);
        when(mapper.findLatestByUserIdAndApplicationCaseId(1L, 20L)).thenReturn(previous);
        doAnswer(invocation -> {
            FitAnalysisResult row = invocation.getArgument(0);
            row.setId(11L);
            row.setCreatedAt(LocalDateTime.of(2026, 6, 12, 10, 0));
            return null;
        }).when(mapper).insertFitAnalysis(any(FitAnalysisResult.class));
        when(mapper.findLearningTasksByFitAnalysisId(11L)).thenReturn(List.of());

        service.generate(1L, 20L);

        verify(mapper).insertHistory(eq(11L), eq(20L), eq(60), anyInt(), anyString());
        verify(mapper, atLeastOnce()).insertConditionMatch(eq(11L), any(), anyString(), anyInt());
        verify(mapper).insertAiUsageLog(eq(1L), eq(20L), eq("FIT_ANALYSIS"), eq("SUCCESS"), anyString(),
                anyInt(), anyInt(), anyInt(), eq(2), eq(null));
    }

    @Test
    void scoreBreakdownNeverExceedsEachMaximumAndSumsToFitScore() {
        FitAnalysisMapper mapper = mock(FitAnalysisMapper.class);
        FitAnalysisServiceImpl service = new FitAnalysisServiceImpl(mapper, mock(MockFitAnalysisAiService.class), mock(NotificationService.class), new ObjectMapper());
        FitAnalysisResult result = FitAnalysisResult.builder()
                .id(11L).applicationCaseId(20L).fitScore(100)
                .conditionMatrix("[]").gapRecommendations("[]").strategyActions("[]")
                .build();
        when(mapper.findLatestByUserIdAndApplicationCaseId(1L, 20L)).thenReturn(result);
        when(mapper.findLearningTasksByFitAnalysisId(11L)).thenReturn(List.of());

        var response = service.getByApplicationCase(1L, 20L);

        assertThat(response.scoreBreakdown()).allSatisfy(item -> assertThat(item.earned()).isLessThanOrEqualTo(item.maximum()));
        assertThat(response.scoreBreakdown().stream().mapToInt(item -> item.earned()).sum()).isEqualTo(100);
    }

    private static FitAnalysisGenerationSource source() {
        FitAnalysisGenerationSource source = new FitAnalysisGenerationSource();
        source.setJobAnalysisId(30L);
        source.setJobPostingId(31L);
        source.setJobPostingRevision(2);
        source.setJobAnalysisCreatedAt(LocalDateTime.of(2026, 6, 10, 10, 0));
        source.setUserProfileId(40L);
        source.setProfileUpdatedAt(LocalDateTime.of(2026, 6, 11, 10, 0));
        source.setCompanyName("테스트기업");
        source.setJobTitle("프론트엔드 개발자");
        source.setRequiredSkills("[\"React\",\"TypeScript\"]");
        source.setPreferredSkills("[\"AWS\"]");
        source.setProfileSkills("[\"React\"]");
        source.setProfileCertificates("[]");
        source.setDesiredJob("프론트엔드 개발자");
        return source;
    }
}
