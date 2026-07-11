package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;
import com.careertuner.applicationcase.domain.ApplicationCaseInitialRun;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseInitialRunMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.JobPostingExtractionProcessor.ExtractionResult;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer;
import com.careertuner.companyanalysis.service.CompanyAnalysisService;
import com.careertuner.fitanalysis.ai.MockFitAnalysisAiService;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;
import com.careertuner.jobanalysis.service.JobAnalysisService;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;
import com.careertuner.notification.mapper.NotificationMapper;
import com.careertuner.runtimesetting.service.RuntimeSettingService;

import tools.jackson.databind.ObjectMapper;

/**
 * #G 연결 회귀: strict 재추출이 PENDING 초기 실행 프로필을 닫은 뒤 <b>실제로</b> 수동 분석 진입 가드가
 * 열리고, 초기 자동 파이프라인은 재개되지 않는지를 <b>상태를 공유하는 fake 프로필 mapper</b> 로 검증한다.
 * (재추출 테스트의 mock 상호작용 검증과 달리, 여기서는 재추출→수동분석→파이프라인이 같은 프로필 상태를
 * 읽고 쓰는 진짜 연결을 잠근다.)
 */
class ReextractionManualAnalysisFlowTest {

    private static final Long USER = 1L;
    private static final Long CASE = 10L;
    private static final Long POSTING = 20L;

    @Test
    void reextractPassOpensManualAnalysesAndKeepsAutoPipelineClosed() {
        Flow flow = new Flow();
        flow.seedPendingProfile();
        flow.stubReextractionHappyPath();

        flow.reextraction.reextract(USER, CASE, "CLAUDE");

        // 재추출이 PENDING 프로필을 원자적으로 FAILED 로 종결했다(공유 상태로 확인).
        assertThat(flow.initialRuns.findByApplicationCaseId(CASE).getState()).isEqualTo("FAILED");

        // 이후 수동 job/company 분석의 프로필 가드가 실제로 열린다(CONFLICT 아님 → strict 위임 도달).
        flow.caseService.createJobAnalysis(USER, CASE, "OPENAI");
        verify(flow.jobAnalysisService).createJobAnalysisStrict(USER, CASE, BAnalysisProvider.OPENAI);
        flow.caseService.createCompanyAnalysis(USER, CASE, "CLAUDE");
        verify(flow.companyAnalysisService).createCompanyAnalysisStrict(USER, CASE, BAnalysisProvider.CLAUDE);

        // 초기 자동 파이프라인은 FAILED 프로필 claim 에 실패해 재개되지 않는다(재추출 후 자동분석 없음 정책).
        flow.runAutoPipeline();
        verify(flow.bAnalysisGenerationService, never()).generateJobAnalysis(any(), any());
        verify(flow.bAnalysisGenerationService, never()).generateJobAnalysisPreferred(any(), any(), any());
        verify(flow.applicationCaseMapper, never()).markAnalysisStarted(anyLong(), anyLong(), any());
    }

    @Test
    void reextractFailureStillOpensManualAnalyses() {
        // 재추출 자체가 실패해도 프로필은 이미 FAILED 로 종결 → 수동 분석·재추출 재시도가 모두 열려 있다.
        Flow flow = new Flow();
        flow.seedPendingProfile();
        flow.stubReextractionHappyPath();
        doThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "OCR 실패"))
                .when(flow.jobPostingService)
                .extractUploadedJobPostingStrict(any(), any(), any(), any(), any());

        flow.reextraction.reextract(USER, CASE, "CLAUDE");

        assertThat(flow.initialRuns.findByApplicationCaseId(CASE).getState()).isEqualTo("FAILED");
        flow.caseService.createJobAnalysis(USER, CASE, "OPENAI");
        verify(flow.jobAnalysisService).createJobAnalysisStrict(USER, CASE, BAnalysisProvider.OPENAI);
    }

    @Test
    void manualAnalysisStaysBlockedWhilePendingWithoutReextract() {
        // 대조군: 재추출이 없으면 PENDING 프로필이 그대로라 수동 분석은 CONFLICT — 가드가 같은 fake 상태를
        // 실제로 읽고 있음을 증명한다(위 테스트의 "열림"이 가드 우회가 아니라 상태 전이 결과라는 뜻).
        Flow flow = new Flow();
        flow.seedPendingProfile();

        assertThatThrownBy(() -> flow.caseService.createJobAnalysis(USER, CASE, "OPENAI"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(flow.jobAnalysisService, never()).createJobAnalysisStrict(anyLong(), anyLong(), any());
    }

    /** 재추출·수동분석·자동파이프라인이 같은 fake 프로필 상태를 공유하는 조립. */
    private static final class Flow {
        final InMemoryInitialRunMapper initialRuns = new InMemoryInitialRunMapper();
        final ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        final ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        final ApplicationCaseAccessService accessService = mock(ApplicationCaseAccessService.class);
        final JobPostingService jobPostingService = mock(JobPostingService.class);
        final JobPostingExtractionProcessor processor = mock(JobPostingExtractionProcessor.class);
        final JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        final CompanyAnalysisService companyAnalysisService = mock(CompanyAnalysisService.class);
        final BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        final RuntimeSettingService runtimeSettingService = mock(RuntimeSettingService.class);
        final ObjectMapper objectMapper = new ObjectMapper();

        final JobPostingReextractionService reextraction = new JobPostingReextractionService(
                accessService, applicationCaseMapper, extractionMapper, initialRuns, jobPostingService,
                processor, synchronousTransactionTemplate());
        final ApplicationCaseServiceImpl caseService = new ApplicationCaseServiceImpl(
                applicationCaseMapper, extractionMapper,
                new ApplicationCaseAccessService(applicationCaseMapper, mock(com.careertuner.jobposting.mapper.JobPostingMapper.class)),
                jobPostingService, jobAnalysisService, companyAnalysisService,
                mock(JobAnalysisMapper.class), mock(OpenAiResponsesClient.class), mock(NotificationMapper.class),
                mock(ApplicationCaseAutoPipelineService.class), initialRuns, reextraction);

        void seedPendingProfile() {
            initialRuns.insertPending(ApplicationCaseInitialRun.builder().applicationCaseId(CASE).build());
        }

        void stubReextractionHappyPath() {
            when(accessService.requireOwned(USER, CASE)).thenReturn(readyCase());
            when(applicationCaseMapper.lockApplicationCaseById(CASE)).thenReturn(readyCase());
            when(extractionMapper.findLatestExtractionByApplicationCaseId(CASE)).thenReturn(
                    ApplicationCaseExtraction.builder()
                            .id(40L).applicationCaseId(CASE).jobPostingId(POSTING).userId(USER)
                            .sourceType("PDF").status("SUCCEEDED").build());
            when(extractionMapper.countActiveExtractionsByApplicationCaseId(CASE)).thenReturn(0);
            doAnswer(invocation -> {
                invocation.<ApplicationCaseExtraction>getArgument(0).setId(41L);
                return null;
            }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
            when(extractionMapper.claimQueuedExtraction(41L)).thenReturn(1);
            when(extractionMapper.findExtractionById(41L)).thenReturn(
                    ApplicationCaseExtraction.builder()
                            .id(41L).applicationCaseId(CASE).jobPostingId(POSTING).userId(USER)
                            .sourceType("PDF").status("SUCCEEDED").build());
            JobPosting posting = JobPosting.builder()
                    .id(POSTING).applicationCaseId(CASE).revision(1)
                    .uploadedFileUrl("local:x.pdf").sourceType("PDF").build();
            when(jobPostingService.getJobPostingDomainForCase(USER, CASE, POSTING)).thenReturn(posting);
            ExtractedPosting extracted = new ExtractedPosting("PDF", posting.getUploadedFileUrl(), null,
                    "재추출 본문", null, "claude", "claude-x");
            when(jobPostingService.extractUploadedJobPostingStrict(USER, CASE, "PDF", posting.getUploadedFileUrl(), "CLAUDE"))
                    .thenReturn(extracted);
            when(processor.evaluate(any(ApplicationCaseExtraction.class), eq(posting), eq(extracted)))
                    .thenReturn(new ExtractionResult(posting, extracted, null, true, null, false));
        }

        /** FAILED 프로필 상태에서 초기 자동 파이프라인을 실행해 본다(claim 실패로 즉시 중단되어야 함). */
        void runAutoPipeline() {
            when(runtimeSettingService.getBoolean(anyString(), anyBoolean())).thenReturn(true);
            when(applicationCaseMapper.findApplicationCaseByIdAndUserId(CASE, USER)).thenReturn(readyCase());
            ApplicationCaseAutoPipelineService pipeline = new ApplicationCaseAutoPipelineService(
                    applicationCaseMapper,
                    initialRuns,
                    mock(JobAnalysisMapper.class),
                    mock(CompanyAnalysisMapper.class),
                    mock(FitAnalysisMapper.class),
                    mock(InterviewMapper.class),
                    new MockFitAnalysisAiService(),
                    objectMapper,
                    bAnalysisGenerationService,
                    new BCompanyAnalysisCanonicalizer(objectMapper),
                    companyAnalysisService,
                    runtimeSettingService,
                    mock(com.careertuner.reward.service.RewardService.class));
            pipeline.runAfterExtractionPass(USER, CASE, POSTING, 2, "공고 본문 텍스트");
        }
    }

    private static ApplicationCase readyCase() {
        return ApplicationCase.builder().id(CASE).userId(USER).status("READY").build();
    }

    private static TransactionTemplate synchronousTransactionTemplate() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
    }

    /** 상태를 실제로 보유하는 in-memory 프로필 mapper — SQL 계약(조건부 전이)을 자바로 미러링한다. */
    private static final class InMemoryInitialRunMapper implements ApplicationCaseInitialRunMapper {

        private final Map<Long, ApplicationCaseInitialRun> rows = new HashMap<>();

        @Override
        public void insertPending(ApplicationCaseInitialRun run) {
            rows.put(run.getApplicationCaseId(), ApplicationCaseInitialRun.builder()
                    .applicationCaseId(run.getApplicationCaseId())
                    .state("PENDING")
                    .jobAnalysisProvider(run.getJobAnalysisProvider())
                    .companyAnalysisProvider(run.getCompanyAnalysisProvider())
                    .build());
        }

        @Override
        public ApplicationCaseInitialRun findByApplicationCaseId(Long applicationCaseId) {
            return rows.get(applicationCaseId);
        }

        @Override
        public int claimForRun(Long applicationCaseId, String executionToken) {
            ApplicationCaseInitialRun row = rows.get(applicationCaseId);
            if (row == null || !"PENDING".equals(row.getState())) {
                return 0;
            }
            row.setState("RUNNING");
            row.setExecutionToken(executionToken);
            row.setStartedAt(LocalDateTime.now());
            return 1;
        }

        @Override
        public int closePendingAsFailed(Long applicationCaseId, String failureReason) {
            ApplicationCaseInitialRun row = rows.get(applicationCaseId);
            if (row == null || !"PENDING".equals(row.getState())) {
                return 0;
            }
            row.setState("FAILED");
            row.setFailureReason(failureReason);
            row.setFinishedAt(LocalDateTime.now());
            return 1;
        }

        @Override
        public int markDone(Long applicationCaseId, String executionToken) {
            ApplicationCaseInitialRun row = rows.get(applicationCaseId);
            if (row == null || !"RUNNING".equals(row.getState())
                    || !executionToken.equals(row.getExecutionToken())) {
                return 0;
            }
            row.setState("DONE");
            row.setFinishedAt(LocalDateTime.now());
            return 1;
        }

        @Override
        public int markFailed(Long applicationCaseId, String executionToken, String failureReason) {
            ApplicationCaseInitialRun row = rows.get(applicationCaseId);
            if (row == null || !"RUNNING".equals(row.getState())
                    || !executionToken.equals(row.getExecutionToken())) {
                return 0;
            }
            row.setState("FAILED");
            row.setFailureReason(failureReason);
            row.setFinishedAt(LocalDateTime.now());
            return 1;
        }

        @Override
        public List<ApplicationCaseInitialRun> findStaleRunning(long timeoutMinutes, int limit) {
            return List.of();
        }
    }
}
