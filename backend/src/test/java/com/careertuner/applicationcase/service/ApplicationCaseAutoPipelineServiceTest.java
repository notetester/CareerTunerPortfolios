package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.ApplicationCaseInitialRun;
import com.careertuner.applicationcase.mapper.ApplicationCaseInitialRunMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.fitanalysis.ai.MockFitAnalysisAiService;
import com.careertuner.fitanalysis.domain.FitAnalysisGenerationSource;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.jobanalysis.domain.JobAnalysis;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;

import tools.jackson.databind.ObjectMapper;

class ApplicationCaseAutoPipelineServiceTest {

    private ApplicationCaseMapper applicationCaseMapper;
    private ApplicationCaseInitialRunMapper initialRunMapper;
    private JobAnalysisMapper jobAnalysisMapper;
    private CompanyAnalysisMapper companyAnalysisMapper;
    private FitAnalysisMapper fitAnalysisMapper;
    private InterviewMapper interviewMapper;
    private com.careertuner.companyanalysis.service.CompanyAnalysisService companyAnalysisService;
    private ObjectMapper objectMapper;
    private ApplicationCaseAutoPipelineService service;

    private static final String POSTING_TEXT = """
            Acme is hiring a Backend Engineer.
            Responsibilities: build Spring APIs, operate MySQL services, and improve Docker deployment.
            Qualifications: Java, Spring Boot, MyBatis, MySQL, REST, Docker, Testing.
            Preferred: React and TypeScript collaboration experience.
            """.repeat(3);

    @BeforeEach
    void setUp() {
        applicationCaseMapper = mock(ApplicationCaseMapper.class);
        initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        jobAnalysisMapper = mock(JobAnalysisMapper.class);
        companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        fitAnalysisMapper = mock(FitAnalysisMapper.class);
        interviewMapper = mock(InterviewMapper.class);
        objectMapper = new ObjectMapper();
        companyAnalysisService = mock(com.careertuner.companyanalysis.service.CompanyAnalysisService.class);
        service = buildService(runtimeSettingServiceReturningFallback());
    }

    /** 같은 mock 협력자로 서비스만 다시 구성한다(runtime_setting 스텁 교체용 — 자동 파이프라인 on/off 테스트). */
    private ApplicationCaseAutoPipelineService buildService(
            com.careertuner.runtimesetting.service.RuntimeSettingService runtimeSettingService) {
        BAnalysisGenerationService bAnalysisGenerationService = new BAnalysisGenerationService(
                new BAnalysisProperties(),
                mock(BLocalLlmClient.class),
                new BJobSentenceClassifier(),
                objectMapper,
                mock(BAnthropicClient.class),
                mock(OpenAiResponsesClient.class));
        return new ApplicationCaseAutoPipelineService(
                applicationCaseMapper,
                initialRunMapper,
                jobAnalysisMapper,
                companyAnalysisMapper,
                fitAnalysisMapper,
                interviewMapper,
                new MockFitAnalysisAiService(),
                objectMapper,
                bAnalysisGenerationService,
                new com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer(objectMapper),
                companyAnalysisService,
                runtimeSettingService,
                mock(com.careertuner.reward.service.RewardService.class));
    }

    @Test
    void runAfterExtractionPassCreatesSelfHostedAnalysisFitStrategyAndInterviewPrep() {
        stubRunnableDraftCase();
        stubAnalysisInsertCallbacks();
        // 등록 경로가 만든 초기 실행 프로필이 PENDING 이고 claim 에 성공하는 실제 흐름.
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(pendingProfile());
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(1);

        service.runAfterExtractionPass(1L, 10L, 20L, 2, POSTING_TEXT);

        ArgumentCaptor<JobAnalysis> jobCaptor = ArgumentCaptor.forClass(JobAnalysis.class);
        verify(jobAnalysisMapper).insertJobAnalysis(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getJobPostingId()).isEqualTo(20L);
        assertThat(jobCaptor.getValue().getJobPostingRevision()).isEqualTo(2);
        assertThat(jobCaptor.getValue().getRequiredSkills()).contains("Java", "Spring Boot", "MySQL");

        ArgumentCaptor<CompanyAnalysis> companyCaptor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper).insertCompanyAnalysis(companyCaptor.capture());
        assertThat(companyCaptor.getValue().getSourceType()).isEqualTo("JOB_POSTING");
        assertThat(companyCaptor.getValue().getCompanySummary()).contains("외부 기업 정보나 OpenAI 폴백은 사용하지 않았습니다");
        assertThat(companyCaptor.getValue().getCompanySummary())
                .doesNotContain("information was summarized", "No external company API");
        // 자동 파이프라인이 사용자 직접 경로와 동일한 웹검색(collectWebEvidence)을 호출하는지 잠근다(웹검색 배선).
        verify(companyAnalysisService).collectWebEvidence(any(ApplicationCase.class));

        ArgumentCaptor<FitAnalysisResult> fitCaptor = ArgumentCaptor.forClass(FitAnalysisResult.class);
        verify(fitAnalysisMapper).insertFitAnalysis(fitCaptor.capture());
        assertThat(fitCaptor.getValue().getModel()).isEqualTo("self-rules-v1");
        assertThat(fitCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        verify(fitAnalysisMapper).insertHistory(eq(70L), eq(10L), eq(null), any(), any());
        verify(fitAnalysisMapper, atLeastOnce()).insertConditionMatch(eq(70L), any(), any(), anyInt());
        verify(fitAnalysisMapper, atLeastOnce()).insertLearningTask(any());

        verify(interviewMapper).insertSession(any(InterviewSession.class));
        verify(interviewMapper, atLeastOnce()).insertQuestion(any(InterviewQuestion.class));
        verify(applicationCaseMapper).markReadyAfterAnalysis(10L, 1L, "DRAFT");

        // 프로필 claim 과 완료 처리가 같은 execution_token 으로 fencing 되는지 잠근다.
        ArgumentCaptor<String> claimToken = ArgumentCaptor.forClass(String.class);
        verify(initialRunMapper).claimForRun(eq(10L), claimToken.capture());
        ArgumentCaptor<String> doneToken = ArgumentCaptor.forClass(String.class);
        verify(initialRunMapper).markDone(eq(10L), doneToken.capture());
        assertThat(doneToken.getValue()).isEqualTo(claimToken.getValue());
        verify(initialRunMapper, never()).markFailed(anyLong(), anyString(), anyString());

        ArgumentCaptor<AiUsageLog> usageCaptor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(applicationCaseMapper, atLeastOnce()).insertAiUsageLog(usageCaptor.capture());
        assertThat(usageCaptor.getAllValues())
                .extracting(AiUsageLog::getFeatureType)
                .contains("JOB_ANALYSIS", "COMPANY_RESEARCH", "FIT_ANALYSIS", "INTERVIEW_QUESTION_GEN");
        assertThat(usageCaptor.getAllValues())
                .allMatch(log -> "self-rules-v1".equals(log.getModel()))
                .allMatch(log -> Integer.valueOf(0).equals(log.getCreditUsed()));
    }

    @Test
    void skipsPipelineWhenInitialRunProfileAlreadyClaimed() {
        stubRunnableDraftCase();
        // 프로필은 있으나 다른 실행이 이미 claim/완료 → claimForRun 이 0행. 초기 파이프라인 재진입 금지.
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(pendingProfile());
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(0);

        service.runAfterExtractionPass(1L, 10L, 20L, 2, POSTING_TEXT);

        verify(jobAnalysisMapper, never()).insertJobAnalysis(any());
        verify(applicationCaseMapper, never()).markAnalysisStarted(anyLong(), anyLong(), anyString());
        verify(applicationCaseMapper, never()).markReadyAfterAnalysis(anyLong(), anyLong(), anyString());
        verify(initialRunMapper, never()).markDone(anyLong(), anyString());
        verify(initialRunMapper, never()).markFailed(anyLong(), anyString(), anyString());
    }

    @Test
    void marksProfileFailedWhenCaseNotInRunnableState() {
        // 프로필 claim 은 성공했지만 케이스가 DRAFT/READY 가 아니라 ANALYZING 진입게이트를 통과하지 못한다.
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L).userId(1L).companyName("Acme").jobTitle("Backend Engineer")
                .sourceType("PDF").status("ANALYZING").build());
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(pendingProfile());
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(1);

        service.runAfterExtractionPass(1L, 10L, 20L, 2, POSTING_TEXT);

        // 파이프라인 미실행 + 프로필은 자신의 토큰으로 FAILED 회수.
        verify(jobAnalysisMapper, never()).insertJobAnalysis(any());
        verify(applicationCaseMapper, never()).markAnalysisStarted(anyLong(), anyLong(), anyString());
        ArgumentCaptor<String> claimToken = ArgumentCaptor.forClass(String.class);
        verify(initialRunMapper).claimForRun(eq(10L), claimToken.capture());
        verify(initialRunMapper).markFailed(eq(10L), eq(claimToken.getValue()), anyString());
        verify(initialRunMapper, never()).markDone(anyLong(), anyString());
    }

    @Test
    void restoresStatusAndFailsProfileWhenPipelineThrows() {
        stubRunnableDraftCase();
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(pendingProfile());
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(1);
        // 파이프라인 도중(웹검색 단계) 런타임 예외 발생.
        doThrow(new RuntimeException("web search boom"))
                .when(companyAnalysisService).collectWebEvidence(any(ApplicationCase.class));

        service.runAfterExtractionPass(1L, 10L, 20L, 2, POSTING_TEXT);

        verify(applicationCaseMapper).restoreAnalysisStatus(10L, 1L, "DRAFT");
        ArgumentCaptor<String> claimToken = ArgumentCaptor.forClass(String.class);
        verify(initialRunMapper).claimForRun(eq(10L), claimToken.capture());
        verify(initialRunMapper).markFailed(eq(10L), eq(claimToken.getValue()), anyString());
        verify(initialRunMapper, never()).markDone(anyLong(), anyString());
        verify(applicationCaseMapper, never()).markReadyAfterAnalysis(anyLong(), anyLong(), anyString());
    }

    @Test
    void runsWithoutProfileForLegacyCasesGatedOnlyByAnalyzingCas() {
        stubRunnableDraftCase();
        stubAnalysisInsertCallbacks();
        // 프로필이 없는 케이스(레거시·수동 create) — claim 없이 ANALYZING 게이트로만 가드하고 정상 실행.
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(null);

        service.runAfterExtractionPass(1L, 10L, 20L, 2, POSTING_TEXT);

        verify(jobAnalysisMapper).insertJobAnalysis(any());
        verify(applicationCaseMapper).markReadyAfterAnalysis(10L, 1L, "DRAFT");
        verify(initialRunMapper, never()).claimForRun(anyLong(), anyString());
        verify(initialRunMapper, never()).markDone(anyLong(), anyString());
        verify(initialRunMapper, never()).markFailed(anyLong(), anyString(), anyString());
    }

    @Test
    void abandonsPendingProfileWhenAutoPipelineDisabled() {
        // [P1 회귀] 자동 파이프라인 OFF 로 조기 반환할 때 프로필을 PENDING 으로 남기면
        // 수동 분석 가드(CONFLICT)가 영구 차단된다 — claim 후 자신의 토큰으로 FAILED 로 닫아야 한다.
        service = buildService(runtimeSettingServiceReturning(false));
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(1);

        service.runAfterExtractionPass(1L, 10L, 20L, 2, POSTING_TEXT);

        ArgumentCaptor<String> claimToken = ArgumentCaptor.forClass(String.class);
        verify(initialRunMapper).claimForRun(eq(10L), claimToken.capture());
        verify(initialRunMapper).markFailed(eq(10L), eq(claimToken.getValue()), anyString());
        // 파이프라인 자체는 실행되지 않는다.
        verify(applicationCaseMapper, never()).findApplicationCaseByIdAndUserId(anyLong(), anyLong());
        verify(jobAnalysisMapper, never()).insertJobAnalysis(any());
        verify(initialRunMapper, never()).markDone(anyLong(), anyString());
    }

    @Test
    void abandonSkipsMarkFailedWhenNoProfileExists() {
        // 프로필 없는 레거시 케이스 — OFF 조기 반환 시 claim 이 0행이라 markFailed 를 부르지 않는다.
        service = buildService(runtimeSettingServiceReturning(false));
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(0);

        service.runAfterExtractionPass(1L, 10L, 20L, 2, POSTING_TEXT);

        verify(initialRunMapper).claimForRun(eq(10L), anyString());
        verify(initialRunMapper, never()).markFailed(anyLong(), anyString(), anyString());
        verify(jobAnalysisMapper, never()).insertJobAnalysis(any());
    }

    @Test
    void abandonsPendingProfileWhenPostingTextBlank() {
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(1);

        service.runAfterExtractionPass(1L, 10L, 20L, 2, "   ");

        ArgumentCaptor<String> claimToken = ArgumentCaptor.forClass(String.class);
        verify(initialRunMapper).claimForRun(eq(10L), claimToken.capture());
        verify(initialRunMapper).markFailed(eq(10L), eq(claimToken.getValue()), anyString());
        verify(jobAnalysisMapper, never()).insertJobAnalysis(any());
    }

    @Test
    void abandonsPendingProfileWhenApplicationCaseNotFound() {
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(null);
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(1);

        service.runAfterExtractionPass(1L, 10L, 20L, 2, POSTING_TEXT);

        ArgumentCaptor<String> claimToken = ArgumentCaptor.forClass(String.class);
        verify(initialRunMapper).claimForRun(eq(10L), claimToken.capture());
        verify(initialRunMapper).markFailed(eq(10L), eq(claimToken.getValue()), anyString());
        verify(jobAnalysisMapper, never()).insertJobAnalysis(any());
    }

    private void stubRunnableDraftCase() {
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Acme")
                .jobTitle("Backend Engineer")
                .sourceType("PDF")
                .status("DRAFT")
                .build());
        when(applicationCaseMapper.markAnalysisStarted(10L, 1L, "DRAFT")).thenReturn(1);
        when(applicationCaseMapper.markReadyAfterAnalysis(10L, 1L, "DRAFT")).thenReturn(1);
    }

    private void stubAnalysisInsertCallbacks() {
        when(fitAnalysisMapper.findGenerationSource(1L, 10L)).thenReturn(fitSource());
        doAnswer(invocation -> {
            invocation.<FitAnalysisResult>getArgument(0).setId(70L);
            return null;
        }).when(fitAnalysisMapper).insertFitAnalysis(any(FitAnalysisResult.class));
        doAnswer(invocation -> {
            invocation.<InterviewSession>getArgument(0).setId(80L);
            return null;
        }).when(interviewMapper).insertSession(any(InterviewSession.class));
    }

    private static ApplicationCaseInitialRun pendingProfile() {
        return ApplicationCaseInitialRun.builder()
                .applicationCaseId(10L)
                .state("PENDING")
                .build();
    }

    /** runtime_setting 미설정 시 @Value 기본값(2번째 인자)을 그대로 돌려주는 스텁 — 기존 동작 보존. */
    private static com.careertuner.runtimesetting.service.RuntimeSettingService runtimeSettingServiceReturningFallback() {
        var svc = mock(com.careertuner.runtimesetting.service.RuntimeSettingService.class);
        when(svc.getBoolean(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        return svc;
    }

    /** 런타임 설정이 고정값을 돌려주는 스텁 — 자동 파이프라인 on/off 강제용. */
    private static com.careertuner.runtimesetting.service.RuntimeSettingService runtimeSettingServiceReturning(boolean value) {
        var svc = mock(com.careertuner.runtimesetting.service.RuntimeSettingService.class);
        when(svc.getBoolean(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(value);
        return svc;
    }

    private static FitAnalysisGenerationSource fitSource() {
        FitAnalysisGenerationSource source = new FitAnalysisGenerationSource();
        source.setJobAnalysisId(60L);
        source.setJobPostingId(20L);
        source.setJobPostingRevision(2);
        source.setCompanyName("Acme");
        source.setJobTitle("Backend Engineer");
        source.setRequiredSkills("[\"Java\",\"Spring Boot\",\"MySQL\",\"Docker\"]");
        source.setPreferredSkills("[\"React\",\"TypeScript\"]");
        source.setDuties("Build APIs and operate production services.");
        source.setProfileSkills("[\"Java\",\"Docker\"]");
        source.setProfileCertificates("[]");
        source.setDesiredJob("Backend Engineer");
        return source;
    }
}
