package com.careertuner.companyanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.applicationcase.service.ApplicationCaseAnalysisStatusService;
import com.careertuner.applicationcase.service.BAnalysisGenerationService;
import com.careertuner.applicationcase.service.BAnalysisGenerationService.StrictCompanyResult;
import com.careertuner.applicationcase.service.BAnalysisJsonValidator;
import com.careertuner.applicationcase.service.BAnalysisProvider;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.CanonicalCompanyAnalysis;
import com.careertuner.companyanalysis.websearch.CompanyEvidenceCollector;
import com.careertuner.companyanalysis.websearch.CompanyIdentity;
import com.careertuner.companyanalysis.websearch.CompanySourceResolver;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchClient;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchException;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchProperties;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.mapper.JobPostingMapper;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;

import tools.jackson.databind.ObjectMapper;

class CompanyAnalysisServiceStrictTest {

    private final ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
    private final JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
    private final CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
    private final BAnalysisGenerationService generationService = mock(BAnalysisGenerationService.class);
    private final AiUsageLogService usageLogService = mock(AiUsageLogService.class);
    private final ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
    private final BCompanyAnalysisCanonicalizer canonicalizer = mock(BCompanyAnalysisCanonicalizer.class);
    private final CompanyWebSearchProperties webProps = new CompanyWebSearchProperties();
    private final CompanySourceResolver sourceResolver = mock(CompanySourceResolver.class);
    private final CompanyWebSearchClient webSearchClient = mock(CompanyWebSearchClient.class);
    private final CompanyEvidenceCollector evidenceCollector = mock(CompanyEvidenceCollector.class);
    private final CompanySearchCacheService cacheService = mock(CompanySearchCacheService.class);

    private CompanyAnalysisService service() {
        return new CompanyAnalysisService(
                new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper),
                companyAnalysisMapper,
                generationService,
                usageLogService,
                statusService,
                runNowTransactionTemplate(),
                mock(BAnalysisJsonValidator.class),
                canonicalizer,
                mock(NotificationService.class),
                webProps,
                sourceResolver,
                webSearchClient,
                evidenceCollector,
                cacheService,
                new ObjectMapper());
    }

    @Test
    void createCompanyAnalysisStrictStoresProvenanceOnSuccess() {
        webProps.setEnabled(false); // 웹검색 OFF → 공고-only, 검색 호출 0
        ApplicationCase applicationCase = applicationCase("READY");
        stubOwnedCaseWithPosting(applicationCase);
        CompanyAnalysisPayload payload = companyPayload(new Usage("openai-gpt", 100, 50, 150));
        when(generationService.generateCompanyAnalysisStrict(eq(applicationCase), anyString(), any(), eq(BAnalysisProvider.OPENAI)))
                .thenReturn(new StrictCompanyResult(payload, List.of(BAnalysisProvider.OPENAI)));
        when(canonicalizer.canonicalizeForStorage(any(), anyLong(), anyInt(), anyString(), any(), any(), any()))
                .thenReturn(new CanonicalCompanyAnalysis(payload, List.of()));
        when(canonicalizer.withoutUnknownMarkers(any())).thenReturn(null);
        when(canonicalizer.extractUnknowns(any())).thenReturn(null);
        when(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(10L)).thenReturn(new CompanyAnalysis());

        service().createCompanyAnalysisStrict(1L, 10L, BAnalysisProvider.OPENAI);

        ArgumentCaptor<CompanyAnalysis> captor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper).insertCompanyAnalysis(captor.capture());
        CompanyAnalysis saved = captor.getValue();
        assertThat(saved.getRequestedProvider()).isEqualTo("OPENAI");
        assertThat(saved.getActualProvider()).isEqualTo("OPENAI");
        assertThat(saved.getActualModel()).isEqualTo("openai-gpt");
        assertThat(saved.getFallbackUsed()).isFalse();
        assertThat(saved.getAttemptPath()).isEqualTo("[\"OPENAI\"]");
        assertThat(saved.getRunMode()).isEqualTo("MANUAL");
        verify(webSearchClient, never()).search(any(), any()); // 웹 OFF → 검색 미호출
    }

    @Test
    void strictCompanyAnalysisReadsLatestPostingOnlyAfterExclusiveGate() {
        // 입력 스냅샷 직렬화 잠금: 배타 획득 → 최신 공고 조회 → 모델 호출 순서 고정(공고를 게이트 앞에서
        // 읽으면 그 사이 끝난 재추출의 새 revision 을 놓치는 경합이 되살아난다 — job 쪽 테스트와 대칭).
        webProps.setEnabled(false);
        ApplicationCase applicationCase = applicationCase("READY");
        stubOwnedCaseWithPosting(applicationCase);
        CompanyAnalysisPayload payload = companyPayload(new Usage("openai-gpt", 100, 50, 150));
        when(generationService.generateCompanyAnalysisStrict(eq(applicationCase), anyString(), any(), eq(BAnalysisProvider.OPENAI)))
                .thenReturn(new StrictCompanyResult(payload, List.of(BAnalysisProvider.OPENAI)));
        when(canonicalizer.canonicalizeForStorage(any(), anyLong(), anyInt(), anyString(), any(), any(), any()))
                .thenReturn(new CanonicalCompanyAnalysis(payload, List.of()));
        when(canonicalizer.withoutUnknownMarkers(any())).thenReturn(null);
        when(canonicalizer.extractUnknowns(any())).thenReturn(null);
        when(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(10L)).thenReturn(new CompanyAnalysis());

        service().createCompanyAnalysisStrict(1L, 10L, BAnalysisProvider.OPENAI);

        InOrder order = inOrder(statusService, applicationCaseMapper, jobPostingMapper, generationService);
        order.verify(statusService).markAnalyzingExclusive(1L, 10L, "READY");
        // 게이트 뒤 지원 건 재조회(재추출이 갱신한 기업명으로 웹 검색·프롬프트 구성) → 공고 조회 → 모델 호출.
        order.verify(applicationCaseMapper).findApplicationCaseByIdAndUserId(10L, 1L);
        order.verify(jobPostingMapper).findLatestJobPostingByCaseId(10L);
        order.verify(generationService)
                .generateCompanyAnalysisStrict(eq(applicationCase), anyString(), any(), eq(BAnalysisProvider.OPENAI));
    }

    @Test
    void autoCompanyAnalysisReadsCaseAndPostingOnlyAfterExclusiveGate() {
        // 비-strict(자동) 기업분석도 같은 규칙 — 배타 획득 → 지원 건 재조회 → 최신 공고 조회 → 모델 호출.
        // 게이트 앞 스냅샷이면 재추출이 갱신한 기업명을 놓쳐 웹 검색이 이전 기업명으로 나갈 수 있다(회귀 잠금).
        webProps.setEnabled(false);
        ApplicationCase applicationCase = applicationCase("READY");
        stubOwnedCaseWithPosting(applicationCase);
        CompanyAnalysisPayload payload = companyPayload(new Usage("openai-gpt", 100, 50, 150));
        when(generationService.generateCompanyAnalysis(eq(applicationCase), anyString(), any()))
                .thenReturn(new BAnalysisGenerationService.GeneratedCompanyAnalysis(payload, null, null));
        when(canonicalizer.canonicalizeForStorage(any(), anyLong(), anyInt(), anyString(), any(), any(), any()))
                .thenReturn(new CanonicalCompanyAnalysis(payload, List.of()));
        when(canonicalizer.withoutUnknownMarkers(any())).thenReturn(null);
        when(canonicalizer.extractUnknowns(any())).thenReturn(null);
        when(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(10L)).thenReturn(new CompanyAnalysis());

        service().createCompanyAnalysis(1L, 10L);

        InOrder order = inOrder(statusService, applicationCaseMapper, jobPostingMapper, generationService);
        order.verify(statusService).markAnalyzingExclusive(1L, 10L, "READY");
        order.verify(applicationCaseMapper).findApplicationCaseByIdAndUserId(10L, 1L);
        order.verify(jobPostingMapper).findLatestJobPostingByCaseId(10L);
        order.verify(generationService).generateCompanyAnalysis(eq(applicationCase), anyString(), any());
    }

    @Test
    void createCompanyAnalysisStrictPreservesExistingOnFailure() {
        webProps.setEnabled(false);
        ApplicationCase applicationCase = applicationCase("READY");
        stubOwnedCaseWithPosting(applicationCase);
        when(generationService.generateCompanyAnalysisStrict(eq(applicationCase), anyString(), any(), eq(BAnalysisProvider.CLAUDE)))
                .thenThrow(new IllegalStateException("Claude 기업 재분석 실패"));

        assertThatThrownBy(() -> service().createCompanyAnalysisStrict(1L, 10L, BAnalysisProvider.CLAUDE))
                .isInstanceOf(IllegalStateException.class);

        verify(companyAnalysisMapper, never()).insertCompanyAnalysis(any(CompanyAnalysis.class)); // 기존 이력 보존
        verify(statusService).restorePreviousStatus(1L, 10L, "READY");
    }

    @Test
    void createCompanyAnalysisStrictDegradesWebSearchFailureToPostingOnlySuccess() {
        // 웹검색 ON 이지만 CompanyWebSearchException → 공고-only 로 degrade(=strict 실패 아님), 선택 모델은 성공.
        webProps.setEnabled(true);
        ApplicationCase applicationCase = applicationCase("READY");
        stubOwnedCaseWithPosting(applicationCase);
        when(sourceResolver.normalizeCompanyName(anyString())).thenReturn("test company");
        when(cacheService.get(anyString())).thenReturn(java.util.Optional.empty());
        when(sourceResolver.buildQueries(any(CompanyIdentity.class))).thenReturn(List.of("test company 채용"));
        when(webSearchClient.search(any(), any())).thenThrow(new CompanyWebSearchException("네이버 검색 실패 status=500"));

        CompanyAnalysisPayload payload = companyPayload(new Usage("openai-gpt", 10, 10, 20));
        when(generationService.generateCompanyAnalysisStrict(eq(applicationCase), anyString(), any(), eq(BAnalysisProvider.OPENAI)))
                .thenReturn(new StrictCompanyResult(payload, List.of(BAnalysisProvider.OPENAI)));
        when(canonicalizer.canonicalizeForStorage(any(), anyLong(), anyInt(), anyString(), any(), any(), any()))
                .thenReturn(new CanonicalCompanyAnalysis(payload, List.of()));
        when(canonicalizer.withoutUnknownMarkers(any())).thenReturn(null);
        when(canonicalizer.extractUnknowns(any())).thenReturn(null);
        when(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(10L)).thenReturn(new CompanyAnalysis());

        service().createCompanyAnalysisStrict(1L, 10L, BAnalysisProvider.OPENAI);

        // 웹검색은 시도됐고(degrade 경로), 선택 모델 strict 는 빈 웹근거(공고-only)로 성공·저장된다.
        verify(webSearchClient).search(any(), any());
        verify(generationService).generateCompanyAnalysisStrict(eq(applicationCase), anyString(), eq(List.of()), eq(BAnalysisProvider.OPENAI));
        ArgumentCaptor<CompanyAnalysis> captor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper).insertCompanyAnalysis(captor.capture());
        assertThat(captor.getValue().getFallbackUsed()).isFalse(); // 웹 degrade 는 모델 fallback 이 아니다
    }

    private void stubOwnedCaseWithPosting(ApplicationCase applicationCase) {
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(
                JobPosting.builder().id(30L).applicationCaseId(10L).revision(2)
                        .extractedText("백엔드 개발자 채용 공고 본문").sourceType("TEXT").build());
    }

    private static ApplicationCase applicationCase(String status) {
        return ApplicationCase.builder().id(10L).userId(1L)
                .companyName("Test Company").jobTitle("Backend Developer").status(status).build();
    }

    private static CompanyAnalysisPayload companyPayload(Usage usage) {
        return new CompanyAnalysisPayload(
                "회사 요약", "최근 이슈", "IT",
                "[]", "면접 포인트",
                "[{\"type\":\"JOB_POSTING\",\"label\":\"공고문\"}]",
                "[]", "[]", "[]", usage);
    }

    private static TransactionTemplate runNowTransactionTemplate() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
    }
}
