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
        return buildService(runtimeSettingService, new BAnalysisGenerationService(
                new BAnalysisProperties(),
                mock(BLocalLlmClient.class),
                new BJobSentenceClassifier(),
                objectMapper,
                mock(BAnthropicClient.class),
                mock(OpenAiResponsesClient.class)));
    }

    /** 생성 서비스를 mock 으로 주입하는 변형 — preferred 경로 호출·provenance 기록 배선(P0-1·P1-4) 검증용. */
    private ApplicationCaseAutoPipelineService buildService(
            com.careertuner.runtimesetting.service.RuntimeSettingService runtimeSettingService,
            BAnalysisGenerationService bAnalysisGenerationService) {
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
    void initialRunUsesSelectedProvidersAndRecordsInitialProvenance() {
        // [P0-1·P1-4] 등록 시 job=CLAUDE·company=OPENAI 를 골랐으면 초기 파이프라인이 preferred 경로로 그 provider 를
        // 우선 실행하고, 결과 행에 provenance 6컬럼(run_mode=INITIAL)을 기록한다.
        stubRunnableDraftCase();
        stubAnalysisInsertCallbacks();
        BAnalysisGenerationService generation = mock(BAnalysisGenerationService.class);
        service = buildService(runtimeSettingServiceReturningFallback(), generation);
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(profileWithProviders("CLAUDE", "OPENAI"));
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(1);
        when(generation.generateJobAnalysisPreferred(any(), anyString(), eq(BAnalysisProvider.CLAUDE)))
                .thenReturn(new BAnalysisGenerationService.GeneratedJobAnalysis(jobPayload(), null, null,
                        new BAnalysisGenerationService.AnalysisProvenance(
                                "CLAUDE", "CLAUDE", "claude-haiku-test", false, "[\"CLAUDE\"]")));
        when(generation.generateCompanyAnalysisPreferred(any(), anyString(), any(), eq(BAnalysisProvider.OPENAI)))
                .thenReturn(new BAnalysisGenerationService.GeneratedCompanyAnalysis(companyPayload(), null, null,
                        new BAnalysisGenerationService.AnalysisProvenance(
                                "OPENAI", "OPENAI", "gpt-test", false, "[\"OPENAI\"]")));

        service.runAfterExtractionPass(1L, 10L, 20L, 2, POSTING_TEXT);

        // preferred 경로 호출(자동 체인 미호출).
        verify(generation).generateJobAnalysisPreferred(any(), anyString(), eq(BAnalysisProvider.CLAUDE));
        verify(generation, never()).generateJobAnalysis(any(), anyString());
        verify(generation).generateCompanyAnalysisPreferred(any(), anyString(), any(), eq(BAnalysisProvider.OPENAI));
        verify(generation, never()).generateCompanyAnalysis(any(), anyString(), any());

        ArgumentCaptor<JobAnalysis> jobCaptor = ArgumentCaptor.forClass(JobAnalysis.class);
        verify(jobAnalysisMapper).insertJobAnalysis(jobCaptor.capture());
        JobAnalysis job = jobCaptor.getValue();
        assertThat(job.getRequestedProvider()).isEqualTo("CLAUDE");
        assertThat(job.getActualProvider()).isEqualTo("CLAUDE");
        assertThat(job.getActualModel()).isEqualTo("claude-haiku-test");
        assertThat(job.getFallbackUsed()).isFalse();
        assertThat(job.getAttemptPath()).isEqualTo("[\"CLAUDE\"]");
        assertThat(job.getRunMode()).isEqualTo("INITIAL");

        ArgumentCaptor<CompanyAnalysis> companyCaptor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper).insertCompanyAnalysis(companyCaptor.capture());
        CompanyAnalysis company = companyCaptor.getValue();
        assertThat(company.getRequestedProvider()).isEqualTo("OPENAI");
        assertThat(company.getActualProvider()).isEqualTo("OPENAI");
        assertThat(company.getActualModel()).isEqualTo("gpt-test");
        assertThat(company.getFallbackUsed()).isFalse();
        assertThat(company.getAttemptPath()).isEqualTo("[\"OPENAI\"]");
        assertThat(company.getRunMode()).isEqualTo("INITIAL");
    }

    @Test
    void initialRunWithoutSelectionUsesAutoChainMarksInitialWithNullProviderProvenance() {
        // 선택이 없으면(프로필 provider NULL) 기존 자동 체인으로 돌지만, run_mode 는 여전히 INITIAL 로 찍는다
        // ("초기 실행은 항상 INITIAL" 계약 — MANUAL·레거시와 구분). provider provenance 컬럼만 NULL 로 남는다.
        stubRunnableDraftCase();
        stubAnalysisInsertCallbacks();
        BAnalysisGenerationService generation = mock(BAnalysisGenerationService.class);
        service = buildService(runtimeSettingServiceReturningFallback(), generation);
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(pendingProfile());
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(1);
        when(generation.generateJobAnalysis(any(), anyString()))
                .thenReturn(new BAnalysisGenerationService.GeneratedJobAnalysis(jobPayload(), null, null));
        when(generation.generateCompanyAnalysis(any(), anyString(), any()))
                .thenReturn(new BAnalysisGenerationService.GeneratedCompanyAnalysis(companyPayload(), null, null));

        service.runAfterExtractionPass(1L, 10L, 20L, 2, POSTING_TEXT);

        verify(generation).generateJobAnalysis(any(), anyString());
        verify(generation, never()).generateJobAnalysisPreferred(any(), anyString(), any());
        verify(generation).generateCompanyAnalysis(any(), anyString(), any());
        verify(generation, never()).generateCompanyAnalysisPreferred(any(), anyString(), any(), any());

        ArgumentCaptor<JobAnalysis> jobCaptor = ArgumentCaptor.forClass(JobAnalysis.class);
        verify(jobAnalysisMapper).insertJobAnalysis(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getRequestedProvider()).isNull();
        assertThat(jobCaptor.getValue().getActualProvider()).isNull();
        assertThat(jobCaptor.getValue().getActualModel()).isNull();
        assertThat(jobCaptor.getValue().getFallbackUsed()).isNull();
        assertThat(jobCaptor.getValue().getAttemptPath()).isNull();
        assertThat(jobCaptor.getValue().getRunMode()).isEqualTo("INITIAL");

        ArgumentCaptor<CompanyAnalysis> companyCaptor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper).insertCompanyAnalysis(companyCaptor.capture());
        assertThat(companyCaptor.getValue().getRequestedProvider()).isNull();
        assertThat(companyCaptor.getValue().getActualProvider()).isNull();
        assertThat(companyCaptor.getValue().getRunMode()).isEqualTo("INITIAL");
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
    void truncatesPipelineFailureReasonToInitialRunColumnLength() {
        // failure_reason 컬럼은 VARCHAR(255) — markFailed 에 넘기는 실패 사유가 255자를 넘으면 Data truncation 으로
        // markFailed 자체가 throw 되어 프로필이 FAILED 로 못 닫히고 RUNNING 에 고착된다(예: fit 스키마 갭의 긴 SQL 에러).
        // 따라서 실패 사유는 반드시 255자 이하로 잘라 넘겨야 한다.
        stubRunnableDraftCase();
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(pendingProfile());
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(1);
        doThrow(new RuntimeException("x".repeat(2000)))
                .when(companyAnalysisService).collectWebEvidence(any(ApplicationCase.class));

        service.runAfterExtractionPass(1L, 10L, 20L, 2, POSTING_TEXT);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(initialRunMapper).markFailed(eq(10L), anyString(), reason.capture());
        assertThat(reason.getValue().length()).isLessThanOrEqualTo(255);
    }

    @Test
    void abandonTruncatesFailureReasonToInitialRunColumnLength() {
        // abandonInitialRunIfPending 도 동일 컬럼에 쓰므로 255자 이하로 잘라야 한다(markFailed truncation 고착 방지).
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(1);

        service.abandonInitialRunIfPending(10L, "y".repeat(2000));

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(initialRunMapper).markFailed(eq(10L), anyString(), reason.capture());
        assertThat(reason.getValue().length()).isLessThanOrEqualTo(255);
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

    @Test
    void doesNotAutoReanalyzeOnEditConfirmWhenInitialRunDone() {
        assertEditConfirmDoesNotAutoReanalyze("DONE");
    }

    @Test
    void doesNotAutoReanalyzeOnEditConfirmWhenInitialRunFailed() {
        assertEditConfirmDoesNotAutoReanalyze("FAILED");
    }

    @Test
    void doesNotAutoReanalyzeOnEditConfirmWhenInitialRunRunning() {
        assertEditConfirmDoesNotAutoReanalyze("RUNNING");
    }

    /**
     * 확정 정책: 초기 실행이 이미 종료(DONE/FAILED)됐거나 진행 중(RUNNING)이면, 공고 수정 확정(applyConfirmedPosting)은
     * 새 revision 만 저장하고 자동 재분석을 돌리지 않는다. claim 은 PENDING 만 성공하므로 이 세 상태는 모두 즉시 중단된다.
     * 갱신된 revision 으로 기존 분석이 stale 로 표시되고, 사용자가 분석 탭에서 모델을 골라 수동 재분석한다.
     * (초기 실행 DONE 을 사용자 확정으로 자동 재분석하도록 우회하면 안 된다 — userInitiated 재도입 방지 잠금.)
     */
    private void assertEditConfirmDoesNotAutoReanalyze(String initialRunState) {
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L).userId(1L).companyName("Acme").jobTitle("Backend Engineer")
                .sourceType("PDF").status("READY").build());
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(profile(initialRunState));
        when(initialRunMapper.claimForRun(eq(10L), anyString())).thenReturn(0);

        // 수정 확정이 넘기는 새 revision(61,4)으로 호출 — claim 실패로 어떤 분석도 생성되지 않아야 한다.
        service.runAfterExtractionPass(1L, 10L, 61L, 4, POSTING_TEXT);

        verify(jobAnalysisMapper, never()).insertJobAnalysis(any());
        verify(companyAnalysisMapper, never()).insertCompanyAnalysis(any());
        verify(fitAnalysisMapper, never()).insertFitAnalysis(any());
        verify(interviewMapper, never()).insertSession(any());
        verify(applicationCaseMapper, never()).markAnalysisStarted(anyLong(), anyLong(), anyString());
        verify(applicationCaseMapper, never()).markReadyAfterAnalysis(anyLong(), anyLong(), anyString());
        verify(initialRunMapper, never()).markDone(anyLong(), anyString());
        verify(initialRunMapper, never()).markFailed(anyLong(), anyString(), anyString());
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
        return profile("PENDING");
    }

    /** 등록 시 job/company 분석 provider 를 고른 PENDING 프로필(preferred 경로 진입 조건). */
    private static ApplicationCaseInitialRun profileWithProviders(String jobProvider, String companyProvider) {
        return ApplicationCaseInitialRun.builder()
                .applicationCaseId(10L)
                .state("PENDING")
                .jobAnalysisProvider(jobProvider)
                .companyAnalysisProvider(companyProvider)
                .build();
    }

    private static OpenAiResponsesClient.JobAnalysisPayload jobPayload() {
        return new OpenAiResponsesClient.JobAnalysisPayload(
                "FULL_TIME", "MID", "[\"Java\"]", "[]", "개발 업무", "경력자", "NORMAL",
                "백엔드 개발자를 위한 공고 분석 요약입니다.", "[]", "[]",
                new OpenAiResponsesClient.Usage("claude-haiku-test", 1, 1, 2));
    }

    private static OpenAiResponsesClient.CompanyAnalysisPayload companyPayload() {
        return new OpenAiResponsesClient.CompanyAnalysisPayload(
                "기업 요약입니다.", "확인 불가", "IT 서비스", "[]", "면접 준비 포인트",
                "[]", "[]", "[]", "[]",
                new OpenAiResponsesClient.Usage("gpt-test", 1, 1, 2));
    }

    private static ApplicationCaseInitialRun profile(String state) {
        return ApplicationCaseInitialRun.builder()
                .applicationCaseId(10L)
                .state(state)
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
