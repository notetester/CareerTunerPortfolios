package com.careertuner.fitanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.fitanalysis.ai.MockFitAnalysisAiService;
import com.careertuner.fitanalysis.certificate.CertificateEvidenceService;
import com.careertuner.fitanalysis.domain.FitAnalysisGateResult;
import com.careertuner.fitanalysis.domain.FitAnalysisGenerationSource;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;
import com.careertuner.notification.service.NotificationService;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

class FitAnalysisServiceImplTest {

    @Test
    void generatePersistsHistoryAndNormalizedConditionMatches() {
        FitAnalysisMapper mapper = mock(FitAnalysisMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        var usageLogService = mock(com.careertuner.applicationcase.service.AiUsageLogService.class);
        FitAnalysisServiceImpl service = new FitAnalysisServiceImpl(mapper, new MockFitAnalysisAiService(), new EvidenceGateService(), mock(NotificationService.class), objectMapper, usageLogService, mock(CertificateEvidenceService.class), transactionTemplate());
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
        verify(usageLogService).recordSuccessValues(eq(1L), eq(20L), eq("FIT_ANALYSIS"), anyString(),
                anyInt(), anyInt(), anyInt(), eq(2));
    }

    @Test
    void generatePersistsEvidenceGateWithoutMutatingScoreOrDecision() {
        FitAnalysisMapper mapper = mock(FitAnalysisMapper.class);
        FitAnalysisServiceImpl service = new FitAnalysisServiceImpl(
                mapper, new MockFitAnalysisAiService(), new EvidenceGateService(),
                mock(NotificationService.class), new ObjectMapper(),
                mock(com.careertuner.applicationcase.service.AiUsageLogService.class), mock(CertificateEvidenceService.class), transactionTemplate());
        when(mapper.findGenerationSource(1L, 20L)).thenReturn(source());
        doAnswer(invocation -> {
            FitAnalysisResult row = invocation.getArgument(0);
            row.setId(11L);
            row.setCreatedAt(LocalDateTime.of(2026, 6, 29, 10, 0));
            return null;
        }).when(mapper).insertFitAnalysis(any(FitAnalysisResult.class));
        when(mapper.findLatestByUserIdAndApplicationCaseId(1L, 20L)).thenReturn(null, FitAnalysisResult.builder()
                .id(11L).applicationCaseId(20L).fitScore(45)
                .conditionMatrix("[]").gapRecommendations("[]").strategyActions("[]").build());
        when(mapper.findLearningTasksByFitAnalysisId(11L)).thenReturn(List.of());

        service.generate(1L, 20L);

        // gate 결정 1행 저장 + evidence 버킷 4개 스냅샷. RAG/rewrite 는 보류(false), 버전 고정.
        ArgumentCaptor<FitAnalysisGateResult> gate = ArgumentCaptor.forClass(FitAnalysisGateResult.class);
        verify(mapper).insertGateResult(gate.capture());
        assertThat(gate.getValue().getEvidenceGateVersion()).isEqualTo("r3-review-first");
        assertThat(gate.getValue().isRagRuntimeEnabled()).isFalse();
        assertThat(gate.getValue().isRewriteApplied()).isFalse();
        assertThat(gate.getValue().getGateStatus()).isIn("PASSED", "REVIEW_REQUIRED", "REJECTED");
        verify(mapper, times(6)).insertEvidenceSource(eq(11L), anyString(), anyBoolean(), anyInt(), anyString());

        // gate 는 점수/판단을 바꾸지 않는다: 저장된 fit_analysis 행의 점수/판단이 규칙엔진 산출 그대로다.
        ArgumentCaptor<FitAnalysisResult> row = ArgumentCaptor.forClass(FitAnalysisResult.class);
        verify(mapper).insertFitAnalysis(row.capture());
        assertThat(row.getValue().getFitScore()).isNotNull();
        assertThat(row.getValue().getApplyDecision()).isNotBlank();
    }

    @Test
    void scoreBreakdownNeverExceedsEachMaximumAndSumsToFitScore() {
        FitAnalysisMapper mapper = mock(FitAnalysisMapper.class);
        FitAnalysisServiceImpl service = new FitAnalysisServiceImpl(mapper, mock(MockFitAnalysisAiService.class), new EvidenceGateService(), mock(NotificationService.class), new ObjectMapper(), mock(com.careertuner.applicationcase.service.AiUsageLogService.class), mock(CertificateEvidenceService.class), transactionTemplate());
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

    @Test
    void careerStrategyExcludesHeldCertsAndMarksThemAsStrengths() {
        FitAnalysisMapper mapper = mock(FitAnalysisMapper.class);
        FitAnalysisServiceImpl service = new FitAnalysisServiceImpl(mapper, mock(MockFitAnalysisAiService.class), new EvidenceGateService(), mock(NotificationService.class), new ObjectMapper(), mock(com.careertuner.applicationcase.service.AiUsageLogService.class), mock(CertificateEvidenceService.class), transactionTemplate());
        var profile = new com.careertuner.fitanalysis.domain.CareerProfileSource();
        profile.setDesiredJob("데이터 엔지니어");
        profile.setProfileCertificates("[\"SQLD\"]");
        when(mapper.findCareerProfile(1L)).thenReturn(profile);

        var strategy = service.careerCertificateStrategy(1L);

        // 데이터 직군 카탈로그(SQLD/ADsP/빅데이터분석기사)에서 보유(SQLD)는 강점으로, 후보에선 제외.
        assertThat(strategy.heldStrengths()).containsExactly("SQLD");
        assertThat(strategy.longTermCandidates())
                .extracting(c -> c.name())
                .containsExactly("ADsP", "빅데이터분석기사");
        // 장기 관점 표현 — 이번 지원 건 전략처럼 말하지 않는다.
        assertThat(strategy.longTermCandidates().get(0).reason()).contains("이번 지원 건과는 별개");
        assertThat(strategy.note()).contains("실무 프로젝트");
    }

    @Test
    void careerStrategyWithoutDesiredJobDegradesHonestly() {
        FitAnalysisMapper mapper = mock(FitAnalysisMapper.class);
        FitAnalysisServiceImpl service = new FitAnalysisServiceImpl(mapper, mock(MockFitAnalysisAiService.class), new EvidenceGateService(), mock(NotificationService.class), new ObjectMapper(), mock(com.careertuner.applicationcase.service.AiUsageLogService.class), mock(CertificateEvidenceService.class), transactionTemplate());
        when(mapper.findCareerProfile(1L)).thenReturn(null);

        var strategy = service.careerCertificateStrategy(1L);

        assertThat(strategy.longTermCandidates()).isEmpty();
        assertThat(strategy.heldStrengths()).isEmpty();
        assertThat(strategy.note()).contains("희망 직무를 등록");
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

    /** 콜백을 즉시 실행하는 테스트용 TransactionTemplate(실제 트랜잭션 없이 쓰기 로직 검증). */
    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
    }
}
