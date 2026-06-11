package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.FitAnalysis;
import com.careertuner.applicationcase.dto.AiUsageFailureResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.dto.CreateApplicationCaseRequest;
import com.careertuner.applicationcase.dto.UpdateApplicationCaseRequest;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.companyanalysis.service.CompanyAnalysisService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.jobanalysis.domain.JobAnalysis;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;
import com.careertuner.jobanalysis.service.JobAnalysisService;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.dto.JobPostingRequest;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.mapper.JobPostingMapper;
import com.careertuner.jobposting.service.JobPostingFileStorage;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.jobposting.service.JobPostingTextExtractor;
import tools.jackson.databind.ObjectMapper;

class ApplicationCaseServiceImplTest {

    @Test
    void deleteApplicationCaseUsesSoftDelete() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, accessService);

        when(applicationCaseMapper.softDeleteApplicationCase(10L, 1L)).thenReturn(1);

        service.delete(1L, 10L);

        verify(applicationCaseMapper).softDeleteApplicationCase(10L, 1L);
        verify(applicationCaseMapper, never()).deleteApplicationCase(10L, 1L);
    }

    @Test
    void listApplicationCasesUsesExplicitDeletedView() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, accessService);
        ApplicationCase deleted = ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .sourceType("TEXT")
                .status("DRAFT")
                .deletedAt(LocalDateTime.now())
                .build();

        when(applicationCaseMapper.findApplicationCasesByUserId(1L, "DELETED", false)).thenReturn(List.of(deleted));

        List<ApplicationCaseResponse> response = service.list(1L, "deleted", true);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(10L);
        verify(applicationCaseMapper).findApplicationCasesByUserId(1L, "DELETED", false);
    }

    @Test
    void listApplicationCasesKeepsIncludeArchivedCompatibilityWhenViewIsOmitted() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, accessService);

        when(applicationCaseMapper.findApplicationCasesByUserId(1L, null, true)).thenReturn(List.of());

        List<ApplicationCaseResponse> response = service.list(1L, null, true);

        assertThat(response).isEmpty();
        verify(applicationCaseMapper).findApplicationCasesByUserId(1L, null, true);
    }

    @Test
    void listApplicationCasesRejectsUnknownView() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, accessService);

        assertThatThrownBy(() -> service.list(1L, "ALL", false))
                .isInstanceOf(BusinessException.class);

        verify(applicationCaseMapper, never()).findApplicationCasesByUserId(any(), any(), anyBoolean());
    }

    @Test
    void restoreApplicationCaseClearsDeletedAndArchivedState() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, accessService);

        when(applicationCaseMapper.restoreDeletedApplicationCase(10L, 1L)).thenReturn(1);

        service.restore(1L, 10L);

        verify(applicationCaseMapper).restoreDeletedApplicationCase(10L, 1L);
    }

    @Test
    void restoreApplicationCaseThrowsNotFoundWhenNoDeletedRowIsUpdated() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, accessService);

        when(applicationCaseMapper.restoreDeletedApplicationCase(10L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> service.restore(1L, 10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void saveJobPostingAppendsNextRevisionWithoutDeletingPreviousPostings() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobPostingService service = new JobPostingService(
                accessService,
                jobPostingMapper,
                usageLogService,
                mock(JobPostingFileStorage.class),
                mock(JobPostingTextExtractor.class));

        ApplicationCase applicationCase = ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .build();
        JobPosting latest = JobPosting.builder()
                .id(31L)
                .applicationCaseId(10L)
                .revision(2)
                .originalText("updated posting")
                .sourceType("TEXT")
                .build();

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.nextRevisionForCase(10L)).thenReturn(2);
        doAnswer(invocation -> {
            JobPosting posting = invocation.getArgument(0);
            posting.setId(31L);
            return null;
        }).when(jobPostingMapper).insertJobPosting(any(JobPosting.class));
        when(jobPostingMapper.findJobPostingByIdAndCaseId(31L, 10L)).thenReturn(latest);

        JobPostingResponse response = service.saveJobPosting(1L, 10L,
                new JobPostingRequest("updated posting", null, null, "TEXT"));

        ArgumentCaptor<JobPosting> postingCaptor = ArgumentCaptor.forClass(JobPosting.class);
        verify(jobPostingMapper, never()).deleteJobPostingsByCaseId(10L);
        verify(jobPostingMapper).insertJobPosting(postingCaptor.capture());
        JobPosting savedPosting = postingCaptor.getValue();
        assertThat(savedPosting.getRevision()).isEqualTo(2);
        assertThat(savedPosting.getOriginalText()).isEqualTo("updated posting");
        assertThat(savedPosting.getSourceType()).isEqualTo("TEXT");
        assertThat(response.revision()).isEqualTo(2);
        assertThat(response.originalText()).isEqualTo("updated posting");
        assertThat(response.sourceType()).isEqualTo("TEXT");
    }

    @Test
    void saveUrlJobPostingWithCorrectedTextDoesNotExtractUrlAgain() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        JobPostingTextExtractor textExtractor = mock(JobPostingTextExtractor.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobPostingService service = new JobPostingService(
                accessService,
                jobPostingMapper,
                usageLogService,
                mock(JobPostingFileStorage.class),
                textExtractor);
        String jobUrl = "https://example.com/jobs/backend";
        String correctedText = "Corrected backend job posting text";
        ApplicationCase applicationCase = ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .build();
        JobPosting latest = JobPosting.builder()
                .id(32L)
                .applicationCaseId(10L)
                .revision(3)
                .uploadedFileUrl(jobUrl)
                .extractedText(correctedText)
                .sourceType("URL")
                .build();

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.nextRevisionForCase(10L)).thenReturn(3);
        doAnswer(invocation -> {
            JobPosting posting = invocation.getArgument(0);
            posting.setId(32L);
            return null;
        }).when(jobPostingMapper).insertJobPosting(any(JobPosting.class));
        when(jobPostingMapper.findJobPostingByIdAndCaseId(32L, 10L)).thenReturn(latest);

        JobPostingResponse response = service.saveJobPosting(1L, 10L,
                new JobPostingRequest(null, jobUrl, correctedText, "URL"));

        ArgumentCaptor<JobPosting> postingCaptor = ArgumentCaptor.forClass(JobPosting.class);
        verify(textExtractor, never()).extractUrl(any());
        verify(jobPostingMapper).insertJobPosting(postingCaptor.capture());
        JobPosting savedPosting = postingCaptor.getValue();
        assertThat(savedPosting.getApplicationCaseId()).isEqualTo(10L);
        assertThat(savedPosting.getRevision()).isEqualTo(3);
        assertThat(savedPosting.getSourceType()).isEqualTo("URL");
        assertThat(savedPosting.getUploadedFileUrl()).isEqualTo(jobUrl);
        assertThat(savedPosting.getOriginalText()).isNull();
        assertThat(savedPosting.getExtractedText()).isEqualTo(correctedText);
        assertThat(response.revision()).isEqualTo(3);
        assertThat(response.sourceType()).isEqualTo("URL");
        assertThat(response.uploadedFileUrl()).isEqualTo(jobUrl);
        assertThat(response.extractedText()).isEqualTo(correctedText);
    }

    @Test
    void saveUrlJobPostingWithCorrectedTextRequiresUrl() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        JobPostingTextExtractor textExtractor = mock(JobPostingTextExtractor.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobPostingService service = new JobPostingService(
                accessService,
                jobPostingMapper,
                usageLogService,
                mock(JobPostingFileStorage.class),
                textExtractor);
        ApplicationCase applicationCase = ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .build();

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);

        assertThatThrownBy(() -> service.saveJobPosting(1L, 10L,
                new JobPostingRequest(null, null, "Corrected backend job posting text", "URL")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("공고 URL이 필요합니다.");

        verify(textExtractor, never()).extractUrl(any());
        verify(jobPostingMapper, never()).insertJobPosting(any(JobPosting.class));
    }

    @Test
    void saveUrlJobPostingWithoutCorrectedTextExtractsUrl() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        JobPostingTextExtractor textExtractor = mock(JobPostingTextExtractor.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobPostingService service = new JobPostingService(
                accessService,
                jobPostingMapper,
                usageLogService,
                mock(JobPostingFileStorage.class),
                textExtractor);
        String jobUrl = "https://example.com/jobs/backend";
        String extractedText = "Extracted backend job posting text";
        ApplicationCase applicationCase = ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .build();
        JobPostingTextExtractor.ExtractedPosting extracted = new JobPostingTextExtractor.ExtractedPosting(
                "URL",
                jobUrl,
                jobUrl,
                extractedText,
                null);
        JobPosting latest = JobPosting.builder()
                .id(33L)
                .applicationCaseId(10L)
                .revision(4)
                .originalText(jobUrl)
                .uploadedFileUrl(jobUrl)
                .extractedText(extractedText)
                .sourceType("URL")
                .build();

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(textExtractor.extractUrl(jobUrl)).thenReturn(extracted);
        when(jobPostingMapper.nextRevisionForCase(10L)).thenReturn(4);
        doAnswer(invocation -> {
            JobPosting posting = invocation.getArgument(0);
            posting.setId(33L);
            return null;
        }).when(jobPostingMapper).insertJobPosting(any(JobPosting.class));
        when(jobPostingMapper.findJobPostingByIdAndCaseId(33L, 10L)).thenReturn(latest);

        JobPostingResponse response = service.saveJobPosting(1L, 10L,
                new JobPostingRequest(null, jobUrl, null, "URL"));

        ArgumentCaptor<JobPosting> postingCaptor = ArgumentCaptor.forClass(JobPosting.class);
        verify(textExtractor).extractUrl(jobUrl);
        verify(jobPostingMapper).insertJobPosting(postingCaptor.capture());
        JobPosting savedPosting = postingCaptor.getValue();
        assertThat(savedPosting.getRevision()).isEqualTo(4);
        assertThat(savedPosting.getSourceType()).isEqualTo("URL");
        assertThat(savedPosting.getUploadedFileUrl()).isEqualTo(jobUrl);
        assertThat(savedPosting.getOriginalText()).isEqualTo(jobUrl);
        assertThat(savedPosting.getExtractedText()).isEqualTo(extractedText);
        assertThat(response.sourceType()).isEqualTo("URL");
        assertThat(response.uploadedFileUrl()).isEqualTo(jobUrl);
        assertThat(response.extractedText()).isEqualTo(extractedText);
    }

    @Test
    void createApplicationCaseStoresAndReturnsDeadlineDate() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, accessService);
        LocalDate deadlineDate = LocalDate.of(2026, 7, 31);

        doAnswer(invocation -> {
            ApplicationCase applicationCase = invocation.getArgument(0);
            applicationCase.setId(10L);
            return null;
        }).when(applicationCaseMapper).insertApplicationCase(any(ApplicationCase.class));
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .deadlineDate(deadlineDate)
                .sourceType("TEXT")
                .status("DRAFT")
                .build());

        ApplicationCaseResponse response = service.create(1L, new CreateApplicationCaseRequest(
                "Test Company",
                "Backend Developer",
                null,
                deadlineDate,
                null,
                null,
                null));

        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        verify(applicationCaseMapper).insertApplicationCase(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getDeadlineDate()).isEqualTo(deadlineDate);
        assertThat(response.deadlineDate()).isEqualTo(deadlineDate);
    }

    @Test
    void updateApplicationCaseClearsDeadlineDateWhenRequested() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, accessService);
        LocalDate deadlineDate = LocalDate.of(2026, 7, 31);
        ApplicationCase existing = ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .postingDate(LocalDate.of(2026, 6, 1))
                .deadlineDate(deadlineDate)
                .sourceType("TEXT")
                .status("DRAFT")
                .build();
        ApplicationCase updated = ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .postingDate(LocalDate.of(2026, 6, 1))
                .deadlineDate(null)
                .sourceType("TEXT")
                .status("DRAFT")
                .build();

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L))
                .thenReturn(existing)
                .thenReturn(updated);

        ApplicationCaseResponse response = service.update(1L, 10L, new UpdateApplicationCaseRequest(
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                null));

        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        verify(applicationCaseMapper).updateApplicationCase(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getDeadlineDate()).isNull();
        assertThat(response.deadlineDate()).isNull();
    }

    @Test
    void createJobAnalysisStoresOnlyBAnalysisAndUsageLog() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, openAiClient, usageLogService, statusService, transactionTemplate(), analysisJsonValidator());

        ApplicationCase applicationCase = applicationCase("DRAFT");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        Usage usage = new Usage("gpt-test", 100, 50, 150);
        JobAnalysisPayload payload = jobAnalysisPayload(usage);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(openAiClient.analyzeJobPosting(applicationCase, "Java Spring REST API")).thenReturn(payload);
        when(jobAnalysisMapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(jobAnalysis());

        JobAnalysisResponse response = service.createJobAnalysis(1L, 10L);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.evidence()).isEqualTo("[{\"field\":\"requiredSkills\",\"quote\":\"Java Spring REST API\"}]");
        assertThat(response.ambiguousConditions()).isEqualTo("[{\"condition\":\"experience is not explicit\",\"assumption\":\"junior\"}]");
        verify(jobAnalysisMapper, never()).deleteJobAnalysesByCaseId(10L);
        verify(jobAnalysisMapper).insertJobAnalysis(any(JobAnalysis.class));
        verify(applicationCaseMapper, never()).insertFitAnalysis(any(FitAnalysis.class));
        verify(statusService).markAnalyzing(1L, 10L, "DRAFT");
        InOrder successOrder = inOrder(statusService, usageLogService);
        successOrder.verify(statusService).markReadyAfterAnalysis(1L, 10L, "DRAFT");
        successOrder.verify(usageLogService).recordSuccess(1L, 10L, "JOB_ANALYSIS", usage);
    }

    @Test
    void createJobAnalysisRejectsAppliedStatusBeforeStartingAiRequest() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, openAiClient, usageLogService, statusService, transactionTemplate(), analysisJsonValidator());

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase("APPLIED"));

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("현재 상태에서는 분석을 다시 실행할 수 없습니다.");

        verify(statusService, never()).markAnalyzing(1L, 10L, "APPLIED");
        verify(openAiClient, never()).analyzeJobPosting(any(ApplicationCase.class), any());
        verify(jobAnalysisMapper, never()).insertJobAnalysis(any(JobAnalysis.class));
        verify(usageLogService, never()).recordSuccess(eq(1L), eq(10L), eq("JOB_ANALYSIS"), any());
        verify(usageLogService, never()).recordFailure(eq(1L), eq(10L), eq("JOB_ANALYSIS"), any());
    }

    @Test
    void createJobAnalysisRejectsAnalyzingStatusBeforeStartingAiRequest() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, openAiClient, usageLogService, statusService, transactionTemplate(), analysisJsonValidator());

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase("ANALYZING"));

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 분석이 진행 중입니다. 잠시 후 결과를 확인해 주세요.");

        verify(statusService, never()).markAnalyzing(1L, 10L, "ANALYZING");
        verify(openAiClient, never()).analyzeJobPosting(any(ApplicationCase.class), any());
        verify(jobAnalysisMapper, never()).insertJobAnalysis(any(JobAnalysis.class));
        verify(usageLogService, never()).recordSuccess(eq(1L), eq(10L), eq("JOB_ANALYSIS"), any());
        verify(usageLogService, never()).recordFailure(eq(1L), eq(10L), eq("JOB_ANALYSIS"), any());
    }

    @Test
    void createJobAnalysisKeepsPreviousAnalysesAndLinksCurrentPostingRevision() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, openAiClient, usageLogService, statusService, transactionTemplate(), analysisJsonValidator());

        ApplicationCase applicationCase = applicationCase("DRAFT");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        Usage usage = new Usage("gpt-test", 100, 50, 150);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(openAiClient.analyzeJobPosting(applicationCase, "Java Spring REST API")).thenReturn(jobAnalysisPayload(usage));
        when(jobAnalysisMapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(jobAnalysis());

        service.createJobAnalysis(1L, 10L);

        ArgumentCaptor<JobAnalysis> analysisCaptor = ArgumentCaptor.forClass(JobAnalysis.class);
        verify(jobAnalysisMapper, never()).deleteJobAnalysesByCaseId(10L);
        verify(jobAnalysisMapper).insertJobAnalysis(analysisCaptor.capture());
        assertThat(analysisCaptor.getValue().getJobPostingId()).isEqualTo(30L);
        assertThat(analysisCaptor.getValue().getJobPostingRevision()).isEqualTo(2);
        assertThat(analysisCaptor.getValue().getEvidence()).isEqualTo("[{\"field\":\"requiredSkills\",\"quote\":\"Java Spring REST API\"}]");
        assertThat(analysisCaptor.getValue().getAmbiguousConditions()).isEqualTo("[{\"condition\":\"experience is not explicit\",\"assumption\":\"junior\"}]");
    }

    @Test
    void createJobAnalysisRestoresPreviousStatusAndRecordsFailure() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, openAiClient, usageLogService, statusService, transactionTemplate(), analysisJsonValidator());
        ApplicationCase applicationCase = applicationCase("READY");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        RuntimeException failure = new RuntimeException("OpenAI down");

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(openAiClient.analyzeJobPosting(applicationCase, "Java Spring REST API")).thenThrow(failure);

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L))
                .isSameAs(failure);

        verify(statusService).markAnalyzing(1L, 10L, "READY");
        verify(statusService).restorePreviousStatus(1L, 10L, "READY");
        verify(usageLogService).recordFailure(1L, 10L, "JOB_ANALYSIS", "OpenAI down");
        verify(jobAnalysisMapper, never()).insertJobAnalysis(any(JobAnalysis.class));
    }

    @Test
    void createJobAnalysisRestoresPreviousStatusWhenSuccessLogFailsAfterReadyAttempt() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, openAiClient, usageLogService, statusService, transactionTemplate(), analysisJsonValidator());
        ApplicationCase applicationCase = applicationCase("DRAFT");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        Usage usage = new Usage("gpt-test", 100, 50, 150);
        RuntimeException failure = new RuntimeException("usage log failed");

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(openAiClient.analyzeJobPosting(applicationCase, "Java Spring REST API")).thenReturn(jobAnalysisPayload(usage));
        when(jobAnalysisMapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(jobAnalysis());
        doThrow(failure).when(usageLogService).recordSuccess(1L, 10L, "JOB_ANALYSIS", usage);

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L))
                .isSameAs(failure);

        verify(statusService).markAnalyzing(1L, 10L, "DRAFT");
        verify(statusService).markReadyAfterAnalysis(1L, 10L, "DRAFT");
        verify(statusService).restorePreviousStatus(1L, 10L, "DRAFT");
        verify(usageLogService).recordFailure(1L, 10L, "JOB_ANALYSIS", "usage log failed");
    }

    @Test
    void createCompanyAnalysisLimitsIndustryToDatabaseColumnLength() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        CompanyAnalysisService service = new CompanyAnalysisService(accessService, companyAnalysisMapper, openAiClient, usageLogService, statusService, transactionTemplate(), analysisJsonValidator());

        ApplicationCase applicationCase = applicationCase("DRAFT");
        JobPosting posting = jobPosting(null, null, "Backend platform job posting");
        Usage usage = new Usage("gpt-test", 100, 50, 150);
        String longIndustry = "A".repeat(130);
        CompanyAnalysisPayload payload = new CompanyAnalysisPayload(
                "Company summary",
                "Recent issues",
                longIndustry,
                "[]",
                "Interview points",
                "[]",
                "[]",
                "[]",
                usage);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(openAiClient.analyzeCompany(applicationCase, "Backend platform job posting")).thenReturn(payload);
        when(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(10L)).thenReturn(CompanyAnalysis.builder()
                .id(20L)
                .applicationCaseId(10L)
                .industry("A".repeat(100))
                .sourceType("JOB_POSTING")
                .build());

        CompanyAnalysisResponse response = service.createCompanyAnalysis(1L, 10L);

        ArgumentCaptor<CompanyAnalysis> companyAnalysisCaptor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper).insertCompanyAnalysis(companyAnalysisCaptor.capture());
        assertThat(companyAnalysisCaptor.getValue().getIndustry()).isEqualTo("A".repeat(100));
        assertThat(response.id()).isEqualTo(20L);
        InOrder successOrder = inOrder(statusService, usageLogService);
        successOrder.verify(statusService).markReadyAfterAnalysis(1L, 10L, "DRAFT");
        successOrder.verify(usageLogService).recordSuccess(1L, 10L, "COMPANY_RESEARCH", usage);
    }

    @Test
    void createCompanyAnalysisStoresMetadataAndLinksCurrentPostingRevision() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        CompanyAnalysisService service = new CompanyAnalysisService(accessService, companyAnalysisMapper, openAiClient, usageLogService, statusService, transactionTemplate(), analysisJsonValidator());

        ApplicationCase applicationCase = applicationCase("DRAFT");
        JobPosting posting = jobPosting(30L, 3, "Backend platform job posting");
        Usage usage = new Usage("gpt-test", 100, 50, 150);
        CompanyAnalysisPayload payload = companyAnalysisPayload(usage);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(openAiClient.analyzeCompany(applicationCase, "Backend platform job posting")).thenReturn(payload);
        when(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(10L)).thenReturn(companyAnalysis());

        LocalDateTime before = LocalDateTime.now();
        CompanyAnalysisResponse response = service.createCompanyAnalysis(1L, 10L);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<CompanyAnalysis> analysisCaptor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper, never()).deleteCompanyAnalysesByCaseId(10L);
        verify(companyAnalysisMapper).insertCompanyAnalysis(analysisCaptor.capture());
        assertThat(analysisCaptor.getValue().getJobPostingId()).isEqualTo(30L);
        assertThat(analysisCaptor.getValue().getJobPostingRevision()).isEqualTo(3);
        assertThat(analysisCaptor.getValue().getVerifiedFacts()).isEqualTo("[{\"fact\":\"job posting mentions B2B platform\",\"source\":\"job posting\"}]");
        assertThat(analysisCaptor.getValue().getAiInferences()).isEqualTo("[{\"inference\":\"platform operations may be discussed\",\"basis\":\"job posting mentions B2B platform\"}]");
        assertThat(analysisCaptor.getValue().getSourceType()).isEqualTo("JOB_POSTING");
        assertThat(analysisCaptor.getValue().getCheckedAt()).isBetween(before, after);
        assertThat(analysisCaptor.getValue().getRefreshRecommendedAt()).isEqualTo(analysisCaptor.getValue().getCheckedAt().plusDays(30));
        assertThat(response.verifiedFacts()).isEqualTo("[{\"fact\":\"job posting mentions B2B platform\",\"source\":\"job posting\"}]");
        assertThat(response.aiInferences()).isEqualTo("[{\"inference\":\"platform operations may be discussed\",\"basis\":\"job posting mentions B2B platform\"}]");
        assertThat(response.sourceType()).isEqualTo("JOB_POSTING");
        verify(statusService).markAnalyzing(1L, 10L, "DRAFT");
    }

    @Test
    void createCompanyAnalysisRejectsClosedStatusBeforeStartingAiRequest() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        CompanyAnalysisService service = new CompanyAnalysisService(accessService, companyAnalysisMapper, openAiClient, usageLogService, statusService, transactionTemplate(), analysisJsonValidator());

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase("CLOSED"));

        assertThatThrownBy(() -> service.createCompanyAnalysis(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("현재 상태에서는 분석을 다시 실행할 수 없습니다.");

        verify(statusService, never()).markAnalyzing(1L, 10L, "CLOSED");
        verify(openAiClient, never()).analyzeCompany(any(ApplicationCase.class), any());
        verify(companyAnalysisMapper, never()).insertCompanyAnalysis(any(CompanyAnalysis.class));
        verify(usageLogService, never()).recordSuccess(eq(1L), eq(10L), eq("COMPANY_RESEARCH"), any());
        verify(usageLogService, never()).recordFailure(eq(1L), eq(10L), eq("COMPANY_RESEARCH"), any());
    }

    @Test
    void createCompanyAnalysisRejectsAnalyzingStatusBeforeStartingAiRequest() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        CompanyAnalysisService service = new CompanyAnalysisService(accessService, companyAnalysisMapper, openAiClient, usageLogService, statusService, transactionTemplate(), analysisJsonValidator());

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase("ANALYZING"));

        assertThatThrownBy(() -> service.createCompanyAnalysis(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 분석이 진행 중입니다. 잠시 후 결과를 확인해 주세요.");

        verify(statusService, never()).markAnalyzing(1L, 10L, "ANALYZING");
        verify(openAiClient, never()).analyzeCompany(any(ApplicationCase.class), any());
        verify(companyAnalysisMapper, never()).insertCompanyAnalysis(any(CompanyAnalysis.class));
        verify(usageLogService, never()).recordSuccess(eq(1L), eq(10L), eq("COMPANY_RESEARCH"), any());
        verify(usageLogService, never()).recordFailure(eq(1L), eq(10L), eq("COMPANY_RESEARCH"), any());
    }

    @Test
    void analysisStatusStartMapperRequiresSameRunnablePreviousStatus() throws Exception {
        String mapperXml = Files.readString(Path.of("src/main/resources/mapper/applicationcase/ApplicationCaseMapper.xml"));

        assertThat(mapperXml).contains("status = #{previousStatus}");
        assertThat(mapperXml).contains("#{previousStatus} IN ('DRAFT', 'READY')");
    }

    @Test
    void analysisStatusServiceThrowsWhenStartTransitionIsNotApplied() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        ApplicationCaseAnalysisStatusService statusService = new ApplicationCaseAnalysisStatusService(applicationCaseMapper);

        when(applicationCaseMapper.markAnalysisStarted(10L, 1L, "READY")).thenReturn(0);

        assertThatThrownBy(() -> statusService.markAnalyzing(1L, 10L, "READY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("분석 상태를 시작 상태로 변경하지 못했습니다.");
    }

    @Test
    void analysisStatusServiceThrowsWhenReadyTransitionIsNotApplied() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        ApplicationCaseAnalysisStatusService statusService = new ApplicationCaseAnalysisStatusService(applicationCaseMapper);

        when(applicationCaseMapper.markReadyAfterAnalysis(10L, 1L, "READY")).thenReturn(0);

        assertThatThrownBy(() -> statusService.markReadyAfterAnalysis(1L, 10L, "READY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("분석 완료 상태로 변경하지 못했습니다.");
    }

    @Test
    void getAiUsageFailuresRequiresOwnedCaseAndReturnsBFailures() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, accessService);
        LocalDateTime createdAt = LocalDateTime.now();
        AiUsageFailureResponse failure = new AiUsageFailureResponse("JOB_ANALYSIS", "OpenAI down", createdAt);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .build());
        when(applicationCaseMapper.findBFailureLogsByCaseId(eq(10L), eq(5))).thenReturn(List.of(failure));

        List<AiUsageFailureResponse> response = service.getAiUsageFailures(1L, 10L, 5);

        assertThat(response).containsExactly(failure);
        verify(applicationCaseMapper).findApplicationCaseByIdAndUserId(10L, 1L);
        verify(applicationCaseMapper).findBFailureLogsByCaseId(10L, 5);
    }

    private static ApplicationCaseServiceImpl applicationCaseService(ApplicationCaseMapper applicationCaseMapper,
                                                                     ApplicationCaseAccessService accessService) {
        return new ApplicationCaseServiceImpl(
                applicationCaseMapper,
                accessService,
                mock(JobPostingService.class),
                mock(JobAnalysisService.class),
                mock(CompanyAnalysisService.class),
                mock(JobAnalysisMapper.class));
    }

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
    }

    private static BAnalysisJsonValidator analysisJsonValidator() {
        return new BAnalysisJsonValidator(new ObjectMapper());
    }

    private static ApplicationCase applicationCase(String status) {
        return ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .status(status)
                .build();
    }

    private static JobPosting jobPosting(Long id, Integer revision, String text) {
        return JobPosting.builder()
                .id(id)
                .applicationCaseId(10L)
                .revision(revision)
                .extractedText(text)
                .sourceType("TEXT")
                .build();
    }

    private static JobAnalysisPayload jobAnalysisPayload(Usage usage) {
        return new JobAnalysisPayload(
                "Full-time",
                "Junior",
                "[\"Java\",\"Spring\"]",
                "[\"MySQL\"]",
                "REST API development",
                "Java and Spring understanding",
                "NORMAL",
                "Backend developer job posting summary",
                "[{\"field\":\"requiredSkills\",\"quote\":\"Java Spring REST API\"}]",
                "[{\"condition\":\"experience is not explicit\",\"assumption\":\"junior\"}]",
                usage);
    }

    private static JobAnalysis jobAnalysis() {
        return JobAnalysis.builder()
                .id(20L)
                .applicationCaseId(10L)
                .jobPostingId(30L)
                .jobPostingRevision(2)
                .employmentType("Full-time")
                .experienceLevel("Junior")
                .requiredSkills("[\"Java\",\"Spring\"]")
                .preferredSkills("[\"MySQL\"]")
                .difficulty("NORMAL")
                .summary("Backend developer job posting summary")
                .evidence("[{\"field\":\"requiredSkills\",\"quote\":\"Java Spring REST API\"}]")
                .ambiguousConditions("[{\"condition\":\"experience is not explicit\",\"assumption\":\"junior\"}]")
                .build();
    }

    private static CompanyAnalysisPayload companyAnalysisPayload(Usage usage) {
        return new CompanyAnalysisPayload(
                "Company summary",
                "Recent issues",
                "IT services",
                "[]",
                "Interview points",
                "[]",
                "[{\"fact\":\"job posting mentions B2B platform\",\"source\":\"job posting\"}]",
                "[{\"inference\":\"platform operations may be discussed\",\"basis\":\"job posting mentions B2B platform\"}]",
                usage);
    }

    private static CompanyAnalysis companyAnalysis() {
        LocalDateTime checkedAt = LocalDateTime.now();
        return CompanyAnalysis.builder()
                .id(20L)
                .applicationCaseId(10L)
                .jobPostingId(30L)
                .jobPostingRevision(3)
                .industry("IT services")
                .verifiedFacts("[{\"fact\":\"job posting mentions B2B platform\",\"source\":\"job posting\"}]")
                .aiInferences("[{\"inference\":\"platform operations may be discussed\",\"basis\":\"job posting mentions B2B platform\"}]")
                .sourceType("JOB_POSTING")
                .checkedAt(checkedAt)
                .refreshRecommendedAt(checkedAt.plusDays(30))
                .build();
    }
}
