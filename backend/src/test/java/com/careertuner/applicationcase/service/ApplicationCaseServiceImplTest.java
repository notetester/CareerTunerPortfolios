package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.ApplicationCaseInitialRun;
import com.careertuner.applicationcase.dto.AiUsageFailureResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseFromJobPostingResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseExtractionResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.dto.CreateApplicationCaseFromJobPostingRequest;
import com.careertuner.applicationcase.dto.CreateApplicationCaseRequest;
import com.careertuner.applicationcase.dto.ConfirmJobPostingExtractionRequest;
import com.careertuner.applicationcase.dto.ReviewJobPostingExtractionRequest;
import com.careertuner.applicationcase.dto.UpdateApplicationCaseRequest;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseInitialRunMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.BAnalysisGenerationService.GeneratedCompanyAnalysis;
import com.careertuner.applicationcase.service.BAnalysisGenerationService.GeneratedJobAnalysis;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;
import com.careertuner.applicationcase.support.BDisplayTime;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.companyanalysis.service.CompanyAnalysisService;
import com.careertuner.companyanalysis.service.CompanySearchCacheService;
import com.careertuner.companyanalysis.websearch.CompanyEvidenceCollector;
import com.careertuner.companyanalysis.websearch.CompanySourceResolver;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchClient;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
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
import com.careertuner.notification.mapper.NotificationMapper;
import com.careertuner.notification.service.NotificationService;
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
    void createFromTextJobPostingQueuesExtractionAndDoesNotExtractMetadata() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService, jobPostingService, openAiClient);
        String postingText = "Acme is hiring a Backend Engineer for Spring API development.";
        JobPostingResponse postingResponse = new JobPostingResponse(
                20L,
                10L,
                1,
                postingText,
                null,
                null,
                "TEXT",
                LocalDateTime.now());

        doAnswer(invocation -> {
            ApplicationCase applicationCase = invocation.getArgument(0);
            applicationCase.setId(10L);
            return null;
        }).when(applicationCaseMapper).insertApplicationCase(any(ApplicationCase.class));
        doAnswer(invocation -> {
            ApplicationCaseExtraction extraction = invocation.getArgument(0);
            extraction.setId(30L);
            return null;
        }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("\uAE30\uC5C5\uBA85 \uD655\uC778 \uD544\uC694")
                .jobTitle("\uC9C1\uBB34\uBA85 \uD655\uC778 \uD544\uC694")
                .sourceType("URL")
                .status("DRAFT")
                .favorite(true)
                .build());
        when(jobPostingService.saveJobPostingForExtractionQueue(eq(1L), eq(10L), any(JobPostingRequest.class)))
                .thenReturn(postingResponse);

        ApplicationCaseFromJobPostingResponse response = service.createFromJobPosting(1L,
                new CreateApplicationCaseFromJobPostingRequest(postingText, null, null, "TEXT", true));

        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        ArgumentCaptor<JobPostingRequest> postingRequestCaptor = ArgumentCaptor.forClass(JobPostingRequest.class);
        ArgumentCaptor<ApplicationCaseExtraction> extractionCaptor = ArgumentCaptor.forClass(ApplicationCaseExtraction.class);
        verify(applicationCaseMapper).insertApplicationCase(caseCaptor.capture());
        verify(jobPostingService).saveJobPostingForExtractionQueue(eq(1L), eq(10L), postingRequestCaptor.capture());
        verify(extractionMapper).insertApplicationCaseExtraction(extractionCaptor.capture());
        verify(openAiClient, never()).extractJobPostingMetadata(any());
        assertThat(caseCaptor.getValue().getCompanyName()).isEqualTo("\uAE30\uC5C5\uBA85 \uD655\uC778 \uD544\uC694");
        assertThat(caseCaptor.getValue().getJobTitle()).isEqualTo("\uC9C1\uBB34\uBA85 \uD655\uC778 \uD544\uC694");
        assertThat(caseCaptor.getValue().getPostingDate()).isNull();
        assertThat(caseCaptor.getValue().getDeadlineDate()).isNull();
        assertThat(caseCaptor.getValue().isFavorite()).isTrue();
        assertThat(postingRequestCaptor.getValue().originalText()).isEqualTo(postingText);
        assertThat(postingRequestCaptor.getValue().sourceType()).isEqualTo("TEXT");
        assertThat(extractionCaptor.getValue().getApplicationCaseId()).isEqualTo(10L);
        assertThat(extractionCaptor.getValue().getJobPostingId()).isEqualTo(20L);
        assertThat(extractionCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(extractionCaptor.getValue().getSourceType()).isEqualTo("TEXT");
        assertThat(extractionCaptor.getValue().getStatus()).isEqualTo("QUEUED");
        assertThat(response.applicationCase().companyName()).isEqualTo("\uAE30\uC5C5\uBA85 \uD655\uC778 \uD544\uC694");
        assertThat(response.jobPosting()).isSameAs(postingResponse);
        assertThat(response.metadata().companyName()).isEqualTo("\uAE30\uC5C5\uBA85 \uD655\uC778 \uD544\uC694");
        assertThat(response.metadata().jobTitle()).isEqualTo("\uC9C1\uBB34\uBA85 \uD655\uC778 \uD544\uC694");
        assertThat(response.metadata().postingDate()).isNull();
        assertThat(response.metadata().deadlineDate()).isNull();
        assertThat(response.extractionJob().id()).isEqualTo(30L);
        assertThat(response.extractionJob().status()).isEqualTo("QUEUED");
    }

    @Test
    void createFromUrlJobPostingQueuesExtractionWithoutFetchingUrl() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService, jobPostingService, openAiClient);
        String jobUrl = "https://example.com/jobs/backend";
        JobPostingResponse postingResponse = new JobPostingResponse(
                20L,
                10L,
                1,
                null,
                jobUrl,
                null,
                "URL",
                LocalDateTime.now());

        doAnswer(invocation -> {
            ApplicationCase applicationCase = invocation.getArgument(0);
            applicationCase.setId(10L);
            return null;
        }).when(applicationCaseMapper).insertApplicationCase(any(ApplicationCase.class));
        doAnswer(invocation -> {
            ApplicationCaseExtraction extraction = invocation.getArgument(0);
            extraction.setId(30L);
            return null;
        }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("기업명 확인 필요")
                .jobTitle("직무명 확인 필요")
                .sourceType("TEXT")
                .status("DRAFT")
                .build());
        when(jobPostingService.saveJobPostingForExtractionQueue(eq(1L), eq(10L), any(JobPostingRequest.class)))
                .thenReturn(postingResponse);

        ApplicationCaseFromJobPostingResponse response = service.createFromJobPosting(1L,
                new CreateApplicationCaseFromJobPostingRequest(null, jobUrl, null, "URL", false));

        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        ArgumentCaptor<JobPostingRequest> postingRequestCaptor = ArgumentCaptor.forClass(JobPostingRequest.class);
        verify(applicationCaseMapper).insertApplicationCase(caseCaptor.capture());
        verify(jobPostingService, never()).extractUrlJobPosting(any());
        verify(jobPostingService).saveJobPostingForExtractionQueue(eq(1L), eq(10L), postingRequestCaptor.capture());
        assertThat(postingRequestCaptor.getValue().uploadedFileUrl()).isEqualTo(jobUrl);
        assertThat(postingRequestCaptor.getValue().sourceType()).isEqualTo("URL");
        assertThat(caseCaptor.getValue().getCompanyName()).isEqualTo("기업명 확인 필요");
        assertThat(caseCaptor.getValue().getJobTitle()).isEqualTo("직무명 확인 필요");
        assertThat(response.applicationCase().companyName()).isEqualTo("기업명 확인 필요");
        assertThat(response.applicationCase().jobTitle()).isEqualTo("직무명 확인 필요");
        assertThat(response.metadata().companyName()).isEqualTo("기업명 확인 필요");
        assertThat(response.metadata().jobTitle()).isEqualTo("직무명 확인 필요");
        assertThat(response.jobPosting()).isSameAs(postingResponse);
        assertThat(response.extractionJob().sourceType()).isEqualTo("URL");
        assertThat(response.extractionJob().status()).isEqualTo("QUEUED");
    }

    @Test
    void createFromJobPostingUploadQueuesExtractionWithoutInvokingOcr() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService, jobPostingService, openAiClient);
        MultipartFile file = mock(MultipartFile.class);
        JobPostingResponse postingResponse = new JobPostingResponse(
                20L,
                10L,
                1,
                null,
                "local:application-postings/10/posting.pdf",
                null,
                "PDF",
                LocalDateTime.now());

        doAnswer(invocation -> {
            ApplicationCase applicationCase = invocation.getArgument(0);
            applicationCase.setId(10L);
            return null;
        }).when(applicationCaseMapper).insertApplicationCase(any(ApplicationCase.class));
        doAnswer(invocation -> {
            ApplicationCaseExtraction extraction = invocation.getArgument(0);
            extraction.setId(30L);
            return null;
        }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("\uAE30\uC5C5\uBA85 \uD655\uC778 \uD544\uC694")
                .jobTitle("\uC9C1\uBB34\uBA85 \uD655\uC778 \uD544\uC694")
                .sourceType("PDF")
                .status("DRAFT")
                .build());
        when(jobPostingService.saveUploadedJobPostingReferenceForNewCase(1L, 10L, file, "PDF"))
                .thenReturn(postingResponse);

        ApplicationCaseFromJobPostingResponse response =
                service.createFromJobPostingUpload(1L, file, "PDF", true, null, null, null);

        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        ArgumentCaptor<ApplicationCaseExtraction> extractionCaptor = ArgumentCaptor.forClass(ApplicationCaseExtraction.class);
        verify(applicationCaseMapper).insertApplicationCase(caseCaptor.capture());
        verify(jobPostingService, never()).uploadJobPostingFileForNewCase(any(), any(), any(), any());
        verify(openAiClient, never()).extractJobPostingMetadata(any());
        verify(extractionMapper).insertApplicationCaseExtraction(extractionCaptor.capture());
        assertThat(caseCaptor.getValue().getCompanyName()).isEqualTo("\uAE30\uC5C5\uBA85 \uD655\uC778 \uD544\uC694");
        assertThat(caseCaptor.getValue().getJobTitle()).isEqualTo("\uC9C1\uBB34\uBA85 \uD655\uC778 \uD544\uC694");
        assertThat(caseCaptor.getValue().isFavorite()).isTrue();
        assertThat(extractionCaptor.getValue().getSourceType()).isEqualTo("PDF");
        assertThat(extractionCaptor.getValue().getStatus()).isEqualTo("QUEUED");
        // OCR provider 미선택 → 추출 큐에 NULL(기본 자동 체인).
        assertThat(extractionCaptor.getValue().getOcrRequestedProvider()).isNull();
        assertThat(response.jobPosting()).isSameAs(postingResponse);
        assertThat(response.metadata().companyName()).isEqualTo("\uAE30\uC5C5\uBA85 \uD655\uC778 \uD544\uC694");
        assertThat(response.metadata().deadlineDate()).isNull();
        assertThat(response.extractionJob().id()).isEqualTo(30L);
    }

    @Test
    void createFromJobPostingUploadCreatesProfileWithSelectedProviders() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseAccessService accessService =
                new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = new ApplicationCaseServiceImpl(
                applicationCaseMapper, extractionMapper, accessService, jobPostingService,
                mock(JobAnalysisService.class), mock(CompanyAnalysisService.class), mock(JobAnalysisMapper.class),
                mock(OpenAiResponsesClient.class), mock(NotificationMapper.class),
                mock(ApplicationCaseAutoPipelineService.class), initialRunMapper,
                mock(JobPostingReextractionService.class));
        MultipartFile file = mock(MultipartFile.class);

        doAnswer(invocation -> {
            ((ApplicationCase) invocation.getArgument(0)).setId(12L);
            return null;
        }).when(applicationCaseMapper).insertApplicationCase(any(ApplicationCase.class));
        doAnswer(invocation -> {
            ((ApplicationCaseExtraction) invocation.getArgument(0)).setId(32L);
            return null;
        }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(12L, 1L)).thenReturn(
                ApplicationCase.builder().id(12L).userId(1L).sourceType("PDF").status("DRAFT").build());
        when(jobPostingService.saveUploadedJobPostingReferenceForNewCase(1L, 12L, file, "PDF")).thenReturn(
                new JobPostingResponse(22L, 12L, 1, null, "local:p.pdf", null, "PDF", LocalDateTime.now()));

        // 업로드(PDF) 등록도 분석 모델 선택값을 보존해야 한다(기본 체인으로 조용히 바뀌면 안 됨).
        service.createFromJobPostingUpload(1L, file, "PDF", true, "claude", "openai", "self_ocr");

        ArgumentCaptor<ApplicationCaseInitialRun> captor = ArgumentCaptor.forClass(ApplicationCaseInitialRun.class);
        verify(initialRunMapper).insertPending(captor.capture());
        assertThat(captor.getValue().getApplicationCaseId()).isEqualTo(12L);
        assertThat(captor.getValue().getJobAnalysisProvider()).isEqualTo("CLAUDE");
        assertThat(captor.getValue().getCompanyAnalysisProvider()).isEqualTo("OPENAI");
        // OCR 선택값도 추출 큐에 정규화되어 스냅샷된다(self_ocr → SELF_OCR).
        ArgumentCaptor<ApplicationCaseExtraction> extractionCaptor = ArgumentCaptor.forClass(ApplicationCaseExtraction.class);
        verify(extractionMapper).insertApplicationCaseExtraction(extractionCaptor.capture());
        assertThat(extractionCaptor.getValue().getOcrRequestedProvider()).isEqualTo("SELF_OCR");
    }

    @Test
    void createFromJobPostingReportsConflictWhenActiveExtractionGuardRejectsInsert() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, openAiClient);
        String postingText = "Acme is hiring a Backend Engineer.";

        doAnswer(invocation -> {
            ApplicationCase applicationCase = invocation.getArgument(0);
            applicationCase.setId(10L);
            return null;
        }).when(applicationCaseMapper).insertApplicationCase(any(ApplicationCase.class));
        when(jobPostingService.saveJobPostingForExtractionQueue(eq(1L), eq(10L), any(JobPostingRequest.class)))
                .thenReturn(new JobPostingResponse(20L, 10L, 1, postingText, null, null, "TEXT", null));
        doThrow(new DuplicateKeyException("uk_case_extraction_active"))
                .when(extractionMapper)
                .insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));

        assertThatThrownBy(() -> service.createFromJobPosting(1L,
                new CreateApplicationCaseFromJobPostingRequest(postingText, null, null, "TEXT", false)))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));

        verify(openAiClient, never()).extractJobPostingMetadata(any());
    }

    @Test
    void directUrlJobPostingSaveWithoutExtractedTextQueuesExtractionInsteadOfExtractingInline() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, mock(OpenAiResponsesClient.class));
        String jobUrl = "https://example.com/jobs/backend";
        JobPostingResponse queuedPosting = new JobPostingResponse(
                20L,
                10L,
                2,
                null,
                jobUrl,
                null,
                "URL",
                null);

        doAnswer(invocation -> {
            ApplicationCaseExtraction extraction = invocation.getArgument(0);
            extraction.setId(30L);
            return null;
        }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
        when(jobPostingService.saveJobPostingForExtractionQueue(eq(1L), eq(10L), any(JobPostingRequest.class)))
                .thenReturn(queuedPosting);

        JobPostingResponse response = service.saveJobPosting(1L, 10L,
                new JobPostingRequest(null, jobUrl, null, "URL"));

        ArgumentCaptor<JobPostingRequest> requestCaptor = ArgumentCaptor.forClass(JobPostingRequest.class);
        ArgumentCaptor<ApplicationCaseExtraction> extractionCaptor = ArgumentCaptor.forClass(ApplicationCaseExtraction.class);
        verify(jobPostingService).saveJobPostingForExtractionQueue(eq(1L), eq(10L), requestCaptor.capture());
        verify(jobPostingService, never()).saveJobPosting(any(), any(), any());
        verify(extractionMapper).insertApplicationCaseExtraction(extractionCaptor.capture());
        verify(applicationCaseMapper).updateApplicationCaseSourceType(10L, 1L, "URL");
        assertThat(requestCaptor.getValue().uploadedFileUrl()).isEqualTo(jobUrl);
        assertThat(requestCaptor.getValue().extractedText()).isNull();
        assertThat(requestCaptor.getValue().sourceType()).isEqualTo("URL");
        assertThat(extractionCaptor.getValue().getApplicationCaseId()).isEqualTo(10L);
        assertThat(extractionCaptor.getValue().getJobPostingId()).isEqualTo(20L);
        assertThat(extractionCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(extractionCaptor.getValue().getSourceType()).isEqualTo("URL");
        assertThat(extractionCaptor.getValue().getStatus()).isEqualTo("QUEUED");
        assertThat(response).isSameAs(queuedPosting);
    }

    @Test
    void directPdfJsonSaveWithoutExtractedTextRejectsUnprocessableBackgroundExtraction() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, mock(OpenAiResponsesClient.class));

        assertThatThrownBy(() -> service.saveJobPosting(1L, 10L,
                new JobPostingRequest("raw pdf text", null, null, "PDF")))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(jobPostingService, never()).saveJobPostingForExtractionQueue(any(), any(), any());
        verify(jobPostingService, never()).saveJobPosting(any(), any(), any());
        verify(extractionMapper, never()).insertApplicationCaseExtraction(any());
    }

    @Test
    void directImageJsonSaveWithoutExtractedTextRejectsExternalReferenceQueueing() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, mock(OpenAiResponsesClient.class));

        assertThatThrownBy(() -> service.saveJobPosting(1L, 10L,
                new JobPostingRequest(null, "https://example.com/posting.png", null, "IMAGE")))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(jobPostingService, never()).saveJobPostingForExtractionQueue(any(), any(), any());
        verify(jobPostingService, never()).saveJobPosting(any(), any(), any());
        verify(extractionMapper, never()).insertApplicationCaseExtraction(any());
    }

    @Test
    void directUrlJobPostingSaveWithExtractedTextKeepsManualSaveWithoutQueueing() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, mock(OpenAiResponsesClient.class));
        String jobUrl = "https://example.com/jobs/backend";
        String extractedText = "Confirmed backend posting text";
        JobPostingResponse savedPosting = new JobPostingResponse(
                21L,
                10L,
                3,
                null,
                jobUrl,
                extractedText,
                "URL",
                null);

        when(jobPostingService.saveJobPosting(eq(1L), eq(10L), any(JobPostingRequest.class)))
                .thenReturn(savedPosting);

        JobPostingResponse response = service.saveJobPosting(1L, 10L,
                new JobPostingRequest(null, jobUrl, extractedText, "URL"));

        verify(jobPostingService).saveJobPosting(eq(1L), eq(10L), any(JobPostingRequest.class));
        verify(jobPostingService, never()).saveJobPostingForExtractionQueue(any(), any(), any());
        verify(extractionMapper, never()).insertApplicationCaseExtraction(any());
        verify(applicationCaseMapper).updateApplicationCaseSourceType(10L, 1L, "URL");
        assertThat(response).isSameAs(savedPosting);
    }

    @Test
    void directPdfJsonSaveWithExtractedTextKeepsManualSaveWithoutQueueing() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, mock(OpenAiResponsesClient.class));
        String extractedText = "Confirmed PDF posting text";
        JobPostingResponse savedPosting = new JobPostingResponse(
                24L,
                10L,
                4,
                "raw pdf text",
                null,
                extractedText,
                "PDF",
                null);

        when(jobPostingService.saveJobPosting(eq(1L), eq(10L), any(JobPostingRequest.class)))
                .thenReturn(savedPosting);

        JobPostingResponse response = service.saveJobPosting(1L, 10L,
                new JobPostingRequest("raw pdf text", null, extractedText, "PDF"));

        verify(jobPostingService).saveJobPosting(eq(1L), eq(10L), any(JobPostingRequest.class));
        verify(jobPostingService, never()).saveJobPostingForExtractionQueue(any(), any(), any());
        verify(extractionMapper, never()).insertApplicationCaseExtraction(any());
        assertThat(response).isSameAs(savedPosting);
    }

    @Test
    void directTextJobPostingSaveStoresPostingAndQueuesMetadataExtraction() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, mock(OpenAiResponsesClient.class));
        String postingText = "Manual posting text";
        JobPostingResponse savedPosting = new JobPostingResponse(
                22L,
                10L,
                4,
                postingText,
                null,
                null,
                "TEXT",
                null);

        when(jobPostingService.saveJobPostingForExtractionQueue(eq(1L), eq(10L), any(JobPostingRequest.class)))
                .thenReturn(savedPosting);
        doAnswer(invocation -> {
            ApplicationCaseExtraction extraction = invocation.getArgument(0);
            extraction.setId(35L);
            return null;
        }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));

        JobPostingResponse response = service.saveJobPosting(1L, 10L,
                new JobPostingRequest(postingText, null, null, "TEXT"));

        ArgumentCaptor<ApplicationCaseExtraction> extractionCaptor = ArgumentCaptor.forClass(ApplicationCaseExtraction.class);
        verify(jobPostingService).saveJobPostingForExtractionQueue(eq(1L), eq(10L), any(JobPostingRequest.class));
        verify(jobPostingService, never()).saveJobPosting(any(), any(), any());
        verify(extractionMapper).insertApplicationCaseExtraction(extractionCaptor.capture());
        verify(applicationCaseMapper).updateApplicationCaseSourceType(10L, 1L, "TEXT");
        assertThat(extractionCaptor.getValue().getApplicationCaseId()).isEqualTo(10L);
        assertThat(extractionCaptor.getValue().getJobPostingId()).isEqualTo(22L);
        assertThat(extractionCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(extractionCaptor.getValue().getSourceType()).isEqualTo("TEXT");
        assertThat(extractionCaptor.getValue().getStatus()).isEqualTo("QUEUED");
        assertThat(response).isSameAs(savedPosting);
    }

    @Test
    void directUploadJobPostingStoresFileReferenceAndQueuesExtractionWithoutOcr() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, mock(OpenAiResponsesClient.class));
        MultipartFile file = mock(MultipartFile.class);
        JobPostingResponse queuedPosting = new JobPostingResponse(
                23L,
                10L,
                5,
                null,
                "local:application-postings/10/posting.pdf",
                null,
                "PDF",
                null);

        doAnswer(invocation -> {
            ApplicationCaseExtraction extraction = invocation.getArgument(0);
            extraction.setId(31L);
            return null;
        }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
        when(jobPostingService.saveUploadedJobPostingReferenceForNewCase(1L, 10L, file, "PDF"))
                .thenReturn(queuedPosting);

        JobPostingResponse response = service.uploadJobPostingFile(1L, 10L, file, "PDF");

        ArgumentCaptor<ApplicationCaseExtraction> extractionCaptor = ArgumentCaptor.forClass(ApplicationCaseExtraction.class);
        verify(jobPostingService).saveUploadedJobPostingReferenceForNewCase(1L, 10L, file, "PDF");
        verify(jobPostingService, never()).uploadJobPostingFile(any(), any(), any(), any());
        verify(extractionMapper).insertApplicationCaseExtraction(extractionCaptor.capture());
        verify(applicationCaseMapper).updateApplicationCaseSourceType(10L, 1L, "PDF");
        assertThat(extractionCaptor.getValue().getApplicationCaseId()).isEqualTo(10L);
        assertThat(extractionCaptor.getValue().getJobPostingId()).isEqualTo(23L);
        assertThat(extractionCaptor.getValue().getSourceType()).isEqualTo("PDF");
        assertThat(extractionCaptor.getValue().getStatus()).isEqualTo("QUEUED");
        assertThat(response).isSameAs(queuedPosting);
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
    void updateApplicationCaseClearsPostingDateWhenRequested() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, accessService);
        LocalDate postingDate = LocalDate.of(2026, 6, 1);
        ApplicationCase existing = ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .postingDate(postingDate)
                .deadlineDate(LocalDate.of(2026, 7, 31))
                .sourceType("TEXT")
                .status("DRAFT")
                .build();
        ApplicationCase updated = ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .postingDate(null)
                .deadlineDate(LocalDate.of(2026, 7, 31))
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
                true,
                null,
                null,
                null,
                null,
                null,
                null));

        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        verify(applicationCaseMapper).updateApplicationCase(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getPostingDate()).isNull();
        assertThat(response.postingDate()).isNull();
    }

    @Test
    void createJobAnalysisStoresOnlyBAnalysisAndUsageLog() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), mock(NotificationService.class));

        ApplicationCase applicationCase = applicationCase("DRAFT");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        Usage usage = new Usage("gpt-test", 100, 50, 150);
        JobAnalysisPayload payload = jobAnalysisPayload(usage);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(bAnalysisGenerationService.generateJobAnalysis(applicationCase, "Java Spring REST API"))
                .thenReturn(new GeneratedJobAnalysis(payload, null, null));
        when(jobAnalysisMapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(jobAnalysis());

        JobAnalysisResponse response = service.createJobAnalysis(1L, 10L);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.evidence()).isEqualTo("[{\"field\":\"requiredSkills\",\"quote\":\"Java Spring REST API\"}]");
        assertThat(response.ambiguousConditions()).isEqualTo("[{\"condition\":\"experience is not explicit\",\"assumption\":\"junior\"}]");
        verify(jobAnalysisMapper).insertJobAnalysis(any(JobAnalysis.class));
        verify(statusService).markAnalyzingExclusive(1L, 10L, "DRAFT");
        InOrder successOrder = inOrder(statusService, usageLogService);
        successOrder.verify(statusService).markReadyAfterAnalysis(1L, 10L, "DRAFT");
        successOrder.verify(usageLogService).recordLocalSuccess(1L, 10L, "JOB_ANALYSIS", usage);
    }

    @Test
    void createJobAnalysisRecordsLocalLlmFailureWhenGeneratorFallsBack() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), mock(NotificationService.class));

        ApplicationCase applicationCase = applicationCase("DRAFT");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        Usage usage = new Usage("self-rules-v1", 100, 50, 150);
        JobAnalysisPayload payload = jobAnalysisPayload(usage);
        String fallbackReason = "Local LLM job analysis failed; fallback to self-rules-v1: model not found";

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(bAnalysisGenerationService.generateJobAnalysis(applicationCase, "Java Spring REST API"))
                .thenReturn(new GeneratedJobAnalysis(payload, fallbackReason, "qwen-test"));
        when(jobAnalysisMapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(jobAnalysis());

        service.createJobAnalysis(1L, 10L);

        InOrder logOrder = inOrder(usageLogService);
        logOrder.verify(usageLogService).recordFailure(1L, 10L, "JOB_ANALYSIS", "qwen-test", fallbackReason);
        logOrder.verify(usageLogService).recordLocalSuccess(1L, 10L, "JOB_ANALYSIS", usage);
    }

    @Test
    void createJobAnalysisRejectsAppliedStatusBeforeStartingAiRequest() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), mock(NotificationService.class));

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase("APPLIED"));

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("현재 상태에서는 분석을 다시 실행할 수 없습니다.");

        verify(statusService, never()).markAnalyzingExclusive(1L, 10L, "APPLIED");
        verify(bAnalysisGenerationService, never()).generateJobAnalysis(any(ApplicationCase.class), any());
        verify(jobAnalysisMapper, never()).insertJobAnalysis(any(JobAnalysis.class));
        verify(usageLogService, never()).recordSuccess(eq(1L), eq(10L), eq("JOB_ANALYSIS"), any());
        verify(usageLogService, never()).recordFailure(eq(1L), eq(10L), eq("JOB_ANALYSIS"), any());
    }

    @Test
    void createJobAnalysisRejectsAnalyzingStatusBeforeStartingAiRequest() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), mock(NotificationService.class));

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase("ANALYZING"));

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 분석이 진행 중입니다. 잠시 후 결과를 확인해 주세요.");

        verify(statusService, never()).markAnalyzingExclusive(1L, 10L, "ANALYZING");
        verify(bAnalysisGenerationService, never()).generateJobAnalysis(any(ApplicationCase.class), any());
        verify(jobAnalysisMapper, never()).insertJobAnalysis(any(JobAnalysis.class));
        verify(usageLogService, never()).recordSuccess(eq(1L), eq(10L), eq("JOB_ANALYSIS"), any());
        verify(usageLogService, never()).recordFailure(eq(1L), eq(10L), eq("JOB_ANALYSIS"), any());
    }

    @Test
    void createJobAnalysisKeepsPreviousAnalysesAndLinksCurrentPostingRevision() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), mock(NotificationService.class));

        ApplicationCase applicationCase = applicationCase("DRAFT");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        Usage usage = new Usage("gpt-test", 100, 50, 150);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(bAnalysisGenerationService.generateJobAnalysis(applicationCase, "Java Spring REST API"))
                .thenReturn(new GeneratedJobAnalysis(jobAnalysisPayload(usage), null, null));
        when(jobAnalysisMapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(jobAnalysis());

        service.createJobAnalysis(1L, 10L);

        ArgumentCaptor<JobAnalysis> analysisCaptor = ArgumentCaptor.forClass(JobAnalysis.class);
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
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), mock(NotificationService.class));
        ApplicationCase applicationCase = applicationCase("READY");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        RuntimeException failure = new RuntimeException("OpenAI down");

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(bAnalysisGenerationService.generateJobAnalysis(applicationCase, "Java Spring REST API")).thenThrow(failure);

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L))
                .isSameAs(failure);

        verify(statusService).markAnalyzingExclusive(1L, 10L, "READY");
        verify(statusService).restorePreviousStatus(1L, 10L, "READY");
        verify(usageLogService).recordFailure(1L, 10L, "JOB_ANALYSIS", "OpenAI down");
        verify(jobAnalysisMapper, never()).insertJobAnalysis(any(JobAnalysis.class));
    }

    @Test
    void createJobAnalysisRestoresPreviousStatusWhenSuccessLogFailsAfterReadyAttempt() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), mock(NotificationService.class));
        ApplicationCase applicationCase = applicationCase("DRAFT");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        Usage usage = new Usage("gpt-test", 100, 50, 150);
        RuntimeException failure = new RuntimeException("usage log failed");

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(bAnalysisGenerationService.generateJobAnalysis(applicationCase, "Java Spring REST API"))
                .thenReturn(new GeneratedJobAnalysis(jobAnalysisPayload(usage), null, null));
        when(jobAnalysisMapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(jobAnalysis());
        doThrow(failure).when(usageLogService).recordLocalSuccess(1L, 10L, "JOB_ANALYSIS", usage);

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L))
                .isSameAs(failure);

        verify(statusService).markAnalyzingExclusive(1L, 10L, "DRAFT");
        verify(statusService).markReadyAfterAnalysis(1L, 10L, "DRAFT");
        verify(statusService).restorePreviousStatus(1L, 10L, "DRAFT");
        verify(usageLogService).recordFailure(1L, 10L, "JOB_ANALYSIS", "usage log failed");
    }

    @Test
    void createCompanyAnalysisLimitsIndustryToDatabaseColumnLength() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        CompanyAnalysisService service = new CompanyAnalysisService(accessService, companyAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), companyAnalysisCanonicalizer(), mock(NotificationService.class), new CompanyWebSearchProperties(), mock(CompanySourceResolver.class), mock(CompanyWebSearchClient.class), mock(CompanyEvidenceCollector.class), mock(CompanySearchCacheService.class), new ObjectMapper());

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
                "[]",
                usage);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(bAnalysisGenerationService.generateCompanyAnalysis(eq(applicationCase), eq("Backend platform job posting"), any()))
                .thenReturn(new GeneratedCompanyAnalysis(payload, null, null));
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
        successOrder.verify(usageLogService).recordLocalSuccess(1L, 10L, "COMPANY_RESEARCH", usage);
    }

    @Test
    void createCompanyAnalysisStoresMetadataAndLinksCurrentPostingRevision() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        CompanyAnalysisService service = new CompanyAnalysisService(accessService, companyAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), companyAnalysisCanonicalizer(), mock(NotificationService.class), new CompanyWebSearchProperties(), mock(CompanySourceResolver.class), mock(CompanyWebSearchClient.class), mock(CompanyEvidenceCollector.class), mock(CompanySearchCacheService.class), new ObjectMapper());

        ApplicationCase applicationCase = applicationCase("DRAFT");
        JobPosting posting = jobPosting(30L, 3, "Backend platform job posting");
        Usage usage = new Usage("gpt-test", 100, 50, 150);
        CompanyAnalysisPayload payload = companyAnalysisPayload(usage);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(bAnalysisGenerationService.generateCompanyAnalysis(eq(applicationCase), eq("Backend platform job posting"), any()))
                .thenReturn(new GeneratedCompanyAnalysis(payload, null, null));
        when(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(10L)).thenReturn(companyAnalysis());

        LocalDateTime before = BDisplayTime.now();
        CompanyAnalysisResponse response = service.createCompanyAnalysis(1L, 10L);
        LocalDateTime after = BDisplayTime.now();

        ArgumentCaptor<CompanyAnalysis> analysisCaptor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper).insertCompanyAnalysis(analysisCaptor.capture());
        assertThat(analysisCaptor.getValue().getJobPostingId()).isEqualTo(30L);
        assertThat(analysisCaptor.getValue().getJobPostingRevision()).isEqualTo(3);
        // 저장 전 canonicalizer 가 factId/sourceKind/sourceRef·inferenceId 를 additive 보정한다(6단계).
        assertThat(analysisCaptor.getValue().getVerifiedFacts()).isEqualTo(
                "[{\"fact\":\"job posting mentions B2B platform\",\"source\":\"job posting\","
                        + "\"evidence\":\"Backend platform job posting\",\"factId\":\"F1\","
                        + "\"sourceKind\":\"JOB_POSTING\",\"sourceRef\":\"jobPosting:30#rev3\"}]");
        assertThat(analysisCaptor.getValue().getAiInferences()).isEqualTo(
                "[{\"inference\":\"platform operations may be discussed\","
                        + "\"basis\":\"job posting mentions B2B platform\",\"confidence\":\"LOW\",\"inferenceId\":\"I1\"}]");
        assertThat(analysisCaptor.getValue().getSourceType()).isEqualTo("JOB_POSTING");
        assertThat(analysisCaptor.getValue().getCheckedAt()).isBetween(before, after);
        assertThat(analysisCaptor.getValue().getRefreshRecommendedAt()).isEqualTo(analysisCaptor.getValue().getCheckedAt().plusDays(30));
        assertThat(response.verifiedFacts()).isEqualTo("[{\"fact\":\"job posting mentions B2B platform\",\"source\":\"job posting\"}]");
        assertThat(response.aiInferences()).isEqualTo("[{\"inference\":\"platform operations may be discussed\",\"basis\":\"job posting mentions B2B platform\"}]");
        assertThat(response.sourceType()).isEqualTo("JOB_POSTING");
        verify(statusService).markAnalyzingExclusive(1L, 10L, "DRAFT");
    }

    @Test
    void createCompanyAnalysisRejectsClosedStatusBeforeStartingAiRequest() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        CompanyAnalysisService service = new CompanyAnalysisService(accessService, companyAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), companyAnalysisCanonicalizer(), mock(NotificationService.class), new CompanyWebSearchProperties(), mock(CompanySourceResolver.class), mock(CompanyWebSearchClient.class), mock(CompanyEvidenceCollector.class), mock(CompanySearchCacheService.class), new ObjectMapper());

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase("CLOSED"));

        assertThatThrownBy(() -> service.createCompanyAnalysis(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("현재 상태에서는 분석을 다시 실행할 수 없습니다.");

        verify(statusService, never()).markAnalyzingExclusive(1L, 10L, "CLOSED");
        verify(bAnalysisGenerationService, never()).generateCompanyAnalysis(any(ApplicationCase.class), any(), any());
        verify(companyAnalysisMapper, never()).insertCompanyAnalysis(any(CompanyAnalysis.class));
        verify(usageLogService, never()).recordSuccess(eq(1L), eq(10L), eq("COMPANY_RESEARCH"), any());
        verify(usageLogService, never()).recordFailure(eq(1L), eq(10L), eq("COMPANY_RESEARCH"), any());
    }

    @Test
    void createCompanyAnalysisRejectsAnalyzingStatusBeforeStartingAiRequest() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        CompanyAnalysisService service = new CompanyAnalysisService(accessService, companyAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), companyAnalysisCanonicalizer(), mock(NotificationService.class), new CompanyWebSearchProperties(), mock(CompanySourceResolver.class), mock(CompanyWebSearchClient.class), mock(CompanyEvidenceCollector.class), mock(CompanySearchCacheService.class), new ObjectMapper());

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase("ANALYZING"));

        assertThatThrownBy(() -> service.createCompanyAnalysis(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 분석이 진행 중입니다. 잠시 후 결과를 확인해 주세요.");

        verify(statusService, never()).markAnalyzingExclusive(1L, 10L, "ANALYZING");
        verify(bAnalysisGenerationService, never()).generateCompanyAnalysis(any(ApplicationCase.class), any(), any());
        verify(companyAnalysisMapper, never()).insertCompanyAnalysis(any(CompanyAnalysis.class));
        verify(usageLogService, never()).recordSuccess(eq(1L), eq(10L), eq("COMPANY_RESEARCH"), any());
        verify(usageLogService, never()).recordFailure(eq(1L), eq(10L), eq("COMPANY_RESEARCH"), any());
    }

    @Test
    void createJobAnalysisRejectedWhileInitialRunInProgress() {
        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseServiceImpl service = serviceWithGuardCollaborators(
                jobAnalysisService, mock(CompanyAnalysisService.class), initialRunMapper);
        // 초기 자동 파이프라인이 아직 RUNNING → 사용자의 수동 재분석은 CONFLICT 로 막는다.
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(initialRun("RUNNING"));

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(jobAnalysisService, never()).createJobAnalysis(anyLong(), anyLong());
    }

    @Test
    void createCompanyAnalysisRejectedWhileInitialRunPending() {
        CompanyAnalysisService companyAnalysisService = mock(CompanyAnalysisService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseServiceImpl service = serviceWithGuardCollaborators(
                mock(JobAnalysisService.class), companyAnalysisService, initialRunMapper);
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(initialRun("PENDING"));

        assertThatThrownBy(() -> service.createCompanyAnalysis(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(companyAnalysisService, never()).createCompanyAnalysis(anyLong(), anyLong());
    }

    @Test
    void manualAnalysisProceedsWhenInitialRunFinishedOrAbsent() {
        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        CompanyAnalysisService companyAnalysisService = mock(CompanyAnalysisService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseServiceImpl service = serviceWithGuardCollaborators(
                jobAnalysisService, companyAnalysisService, initialRunMapper);
        // 프로필이 DONE 이면(또는 아예 없으면) 기존 재분석 경로를 그대로 통과시킨다.
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(initialRun("DONE"));
        when(initialRunMapper.findByApplicationCaseId(11L)).thenReturn(null);

        service.createJobAnalysis(1L, 10L);
        service.createCompanyAnalysis(1L, 11L);

        verify(jobAnalysisService).createJobAnalysis(1L, 10L);
        verify(companyAnalysisService).createCompanyAnalysis(1L, 11L);
    }

    // ── strict 수동 재분석: 컨트롤러 → provider 필수 overload. 누락·무효 400(부수효과·guard 전), 초기실행 409,
    //    strict overload 위임, 무인자 자동 메서드 미호출. ──

    @Test
    void strictJobAnalysisRejectsMissingProviderBeforeGuardAndDelegate() {
        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseServiceImpl service = serviceWithGuardCollaborators(
                jobAnalysisService, mock(CompanyAnalysisService.class), initialRunMapper);

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verify(initialRunMapper, never()).findByApplicationCaseId(anyLong()); // provider 검증이 guard 보다 먼저
        verify(jobAnalysisService, never()).createJobAnalysisStrict(anyLong(), anyLong(), any());
        verify(jobAnalysisService, never()).createJobAnalysis(anyLong(), anyLong()); // 무인자 자동 경로 미도달
    }

    @Test
    void strictJobAnalysisRejectsInvalidProvider() {
        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseServiceImpl service = serviceWithGuardCollaborators(
                jobAnalysisService, mock(CompanyAnalysisService.class), initialRunMapper);

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L, "GEMINI"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verify(jobAnalysisService, never()).createJobAnalysisStrict(anyLong(), anyLong(), any());
        verify(jobAnalysisService, never()).createJobAnalysis(anyLong(), anyLong());
    }

    @Test
    void strictJobAnalysisRejectedWhileInitialRunInProgress() {
        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseServiceImpl service = serviceWithGuardCollaborators(
                jobAnalysisService, mock(CompanyAnalysisService.class), initialRunMapper);
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(initialRun("RUNNING"));

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L, "CLAUDE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(jobAnalysisService, never()).createJobAnalysisStrict(anyLong(), anyLong(), any());
    }

    @Test
    void strictJobAnalysisRejectedWhileInitialRunPending() {
        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseServiceImpl service = serviceWithGuardCollaborators(
                jobAnalysisService, mock(CompanyAnalysisService.class), initialRunMapper);
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(initialRun("PENDING"));

        assertThatThrownBy(() -> service.createJobAnalysis(1L, 10L, "CLAUDE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(jobAnalysisService, never()).createJobAnalysisStrict(anyLong(), anyLong(), any());
    }

    @Test
    void strictJobAnalysisDelegatesToStrictOverloadNotAutoMethod() {
        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseServiceImpl service = serviceWithGuardCollaborators(
                jobAnalysisService, mock(CompanyAnalysisService.class), initialRunMapper);
        when(initialRunMapper.findByApplicationCaseId(10L)).thenReturn(initialRun("DONE"));

        service.createJobAnalysis(1L, 10L, "claude"); // 대소문자 무시 정규화

        verify(jobAnalysisService).createJobAnalysisStrict(1L, 10L, BAnalysisProvider.CLAUDE);
        verify(jobAnalysisService, never()).createJobAnalysis(anyLong(), anyLong());
    }

    @Test
    void strictCompanyAnalysisRejectsMissingProviderAndDelegatesWhenValid() {
        CompanyAnalysisService companyAnalysisService = mock(CompanyAnalysisService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseServiceImpl service = serviceWithGuardCollaborators(
                mock(JobAnalysisService.class), companyAnalysisService, initialRunMapper);

        assertThatThrownBy(() -> service.createCompanyAnalysis(1L, 10L, "  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(companyAnalysisService, never()).createCompanyAnalysisStrict(anyLong(), anyLong(), any());
        verify(companyAnalysisService, never()).createCompanyAnalysis(anyLong(), anyLong());

        when(initialRunMapper.findByApplicationCaseId(11L)).thenReturn(null);
        service.createCompanyAnalysis(1L, 11L, "OPENAI");
        verify(companyAnalysisService).createCompanyAnalysisStrict(1L, 11L, BAnalysisProvider.OPENAI);
        verify(companyAnalysisService, never()).createCompanyAnalysis(anyLong(), anyLong());
    }

    // ── JobAnalysisService strict: 성공 시 6 provenance 저장, 실패 시 insert 없음·이전 상태 복원(기존 이력 보존). ──

    @Test
    void createJobAnalysisStrictStoresProvenanceOnSuccess() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), mock(NotificationService.class));

        ApplicationCase applicationCase = applicationCase("READY");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        Usage usage = new Usage("claude-haiku", 100, 50, 150);
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(bAnalysisGenerationService.generateJobAnalysisStrict(applicationCase, "Java Spring REST API", BAnalysisProvider.CLAUDE))
                .thenReturn(new BAnalysisGenerationService.StrictJobResult(jobAnalysisPayload(usage), List.of(BAnalysisProvider.CLAUDE)));
        when(jobAnalysisMapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(jobAnalysis());

        service.createJobAnalysisStrict(1L, 10L, BAnalysisProvider.CLAUDE);

        ArgumentCaptor<JobAnalysis> captor = ArgumentCaptor.forClass(JobAnalysis.class);
        verify(jobAnalysisMapper).insertJobAnalysis(captor.capture());
        JobAnalysis saved = captor.getValue();
        assertThat(saved.getRequestedProvider()).isEqualTo("CLAUDE");
        assertThat(saved.getActualProvider()).isEqualTo("CLAUDE");
        assertThat(saved.getActualModel()).isEqualTo("claude-haiku");
        assertThat(saved.getFallbackUsed()).isFalse();
        assertThat(saved.getAttemptPath()).isEqualTo("[\"CLAUDE\"]");
        assertThat(saved.getRunMode()).isEqualTo("MANUAL");
        verify(statusService).markReadyAfterAnalysis(1L, 10L, "READY");
    }

    @Test
    void strictJobAnalysisReadsLatestPostingOnlyAfterExclusiveGate() {
        // 입력 스냅샷 직렬화 잠금: 배타 획득(markAnalyzingExclusive) → 최신 공고 조회 → 모델 호출 순서를
        // 고정한다. 공고를 게이트 앞에서 읽으면 그 사이 끝난 재추출의 새 revision 을 놓치고 이전 revision 으로
        // 분석하는 경합이 되살아난다(회귀 방지).
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, bAnalysisGenerationService,
                mock(AiUsageLogService.class), statusService, transactionTemplate(), analysisJsonValidator(),
                mock(NotificationService.class));
        ApplicationCase applicationCase = applicationCase("READY");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        Usage usage = new Usage("claude-haiku", 100, 50, 150);
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(bAnalysisGenerationService.generateJobAnalysisStrict(applicationCase, "Java Spring REST API", BAnalysisProvider.CLAUDE))
                .thenReturn(new BAnalysisGenerationService.StrictJobResult(jobAnalysisPayload(usage), List.of(BAnalysisProvider.CLAUDE)));
        when(jobAnalysisMapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(jobAnalysis());

        service.createJobAnalysisStrict(1L, 10L, BAnalysisProvider.CLAUDE);

        InOrder order = inOrder(statusService, applicationCaseMapper, jobPostingMapper, bAnalysisGenerationService);
        order.verify(statusService).markAnalyzingExclusive(1L, 10L, "READY");
        // 게이트 뒤 지원 건 재조회(재추출이 갱신한 기업명·직무명 반영) → 최신 공고 조회 → 모델 호출.
        order.verify(applicationCaseMapper).findApplicationCaseByIdAndUserId(10L, 1L);
        order.verify(jobPostingMapper).findLatestJobPostingByCaseId(10L);
        order.verify(bAnalysisGenerationService)
                .generateJobAnalysisStrict(applicationCase, "Java Spring REST API", BAnalysisProvider.CLAUDE);
    }

    @Test
    void autoJobAnalysisReadsLatestPostingOnlyAfterExclusiveGate() {
        // AutoPrep 등 비-strict 경로도 같은 배타 획득 + 획득 후 공고 조회를 쓴다(재추출과 동일 규칙).
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, bAnalysisGenerationService,
                mock(AiUsageLogService.class), statusService, transactionTemplate(), analysisJsonValidator(),
                mock(NotificationService.class));
        ApplicationCase applicationCase = applicationCase("READY");
        JobPosting posting = jobPosting(30L, 2, "Java Spring REST API");
        Usage usage = new Usage("gpt-test", 100, 50, 150);
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(bAnalysisGenerationService.generateJobAnalysis(applicationCase, "Java Spring REST API"))
                .thenReturn(new GeneratedJobAnalysis(jobAnalysisPayload(usage), null, null));
        when(jobAnalysisMapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(jobAnalysis());

        service.createJobAnalysis(1L, 10L);

        InOrder order = inOrder(statusService, applicationCaseMapper, jobPostingMapper, bAnalysisGenerationService);
        order.verify(statusService).markAnalyzingExclusive(1L, 10L, "READY");
        // 게이트 뒤 지원 건 재조회 → 최신 공고 조회 → 모델 호출(4경로 공통 규칙).
        order.verify(applicationCaseMapper).findApplicationCaseByIdAndUserId(10L, 1L);
        order.verify(jobPostingMapper).findLatestJobPostingByCaseId(10L);
        order.verify(bAnalysisGenerationService).generateJobAnalysis(applicationCase, "Java Spring REST API");
    }

    @Test
    void createJobAnalysisStrictPreservesExistingOnFailure() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        BAnalysisGenerationService bAnalysisGenerationService = mock(BAnalysisGenerationService.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAnalysisStatusService statusService = mock(ApplicationCaseAnalysisStatusService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        JobAnalysisService service = new JobAnalysisService(accessService, jobAnalysisMapper, bAnalysisGenerationService, usageLogService, statusService, transactionTemplate(), analysisJsonValidator(), mock(NotificationService.class));

        ApplicationCase applicationCase = applicationCase("READY");
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(jobPosting(30L, 2, "Java Spring REST API"));
        when(bAnalysisGenerationService.generateJobAnalysisStrict(applicationCase, "Java Spring REST API", BAnalysisProvider.CLAUDE))
                .thenThrow(new IllegalStateException("Claude 재분석 실패"));

        assertThatThrownBy(() -> service.createJobAnalysisStrict(1L, 10L, BAnalysisProvider.CLAUDE))
                .isInstanceOf(IllegalStateException.class);

        verify(jobAnalysisMapper, never()).insertJobAnalysis(any(JobAnalysis.class)); // 기존 이력 보존
        verify(statusService).restorePreviousStatus(1L, 10L, "READY");
    }

    private static ApplicationCaseServiceImpl serviceWithGuardCollaborators(
            JobAnalysisService jobAnalysisService,
            CompanyAnalysisService companyAnalysisService,
            ApplicationCaseInitialRunMapper initialRunMapper) {
        return new ApplicationCaseServiceImpl(
                mock(ApplicationCaseMapper.class),
                mock(ApplicationCaseExtractionMapper.class),
                mock(ApplicationCaseAccessService.class),
                mock(JobPostingService.class),
                jobAnalysisService,
                companyAnalysisService,
                mock(JobAnalysisMapper.class),
                mock(OpenAiResponsesClient.class),
                mock(NotificationMapper.class),
                mock(ApplicationCaseAutoPipelineService.class),
                initialRunMapper,
                mock(JobPostingReextractionService.class));
    }

    private static ApplicationCaseInitialRun initialRun(String state) {
        return ApplicationCaseInitialRun.builder()
                .applicationCaseId(10L)
                .state(state)
                .build();
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
        ApplicationCaseAnalysisStatusService statusService = new ApplicationCaseAnalysisStatusService(
                applicationCaseMapper, mock(ApplicationCaseExtractionMapper.class));

        when(applicationCaseMapper.markAnalysisStarted(10L, 1L, "READY")).thenReturn(0);

        assertThatThrownBy(() -> statusService.markAnalyzing(1L, 10L, "READY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("분석 상태를 시작 상태로 변경하지 못했습니다.");
    }

    @Test
    void analysisStatusServiceExclusiveRejectsWhenReextractionIsActive() {
        // 수동(strict) 분석 획득은 케이스 행 잠금 + 활성 추출 검사 + ANALYZING CAS 를 한 TX 로 묶는다.
        // 재추출(QUEUED/RUNNING)이 진행 중이면 CONFLICT — ANALYZING 전이를 시도하지 않는다.
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseAnalysisStatusService statusService = new ApplicationCaseAnalysisStatusService(
                applicationCaseMapper, extractionMapper);
        when(extractionMapper.countActiveExtractionsByApplicationCaseId(10L)).thenReturn(1);

        assertThatThrownBy(() -> statusService.markAnalyzingExclusive(1L, 10L, "READY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("공고 재추출이 진행 중입니다");
        verify(applicationCaseMapper).lockApplicationCaseById(10L); // 직렬화 지점(행 잠금) 후 검사
        verify(applicationCaseMapper, never()).markAnalysisStarted(anyLong(), anyLong(), any());
    }

    @Test
    void analysisStatusServiceExclusiveMarksAnalyzingAfterLockWhenNoActiveExtraction() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseAnalysisStatusService statusService = new ApplicationCaseAnalysisStatusService(
                applicationCaseMapper, extractionMapper);
        when(extractionMapper.countActiveExtractionsByApplicationCaseId(10L)).thenReturn(0);
        when(applicationCaseMapper.markAnalysisStarted(10L, 1L, "READY")).thenReturn(1);

        statusService.markAnalyzingExclusive(1L, 10L, "READY");

        InOrder order = inOrder(applicationCaseMapper, extractionMapper);
        order.verify(applicationCaseMapper).lockApplicationCaseById(10L);
        order.verify(extractionMapper).countActiveExtractionsByApplicationCaseId(10L);
        order.verify(applicationCaseMapper).markAnalysisStarted(10L, 1L, "READY");
    }

    @Test
    void analysisStatusServiceThrowsWhenReadyTransitionIsNotApplied() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        ApplicationCaseAnalysisStatusService statusService = new ApplicationCaseAnalysisStatusService(
                applicationCaseMapper, mock(ApplicationCaseExtractionMapper.class));

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

        // 운영 코드가 createdAt 을 UTC→KST(BDisplayTime.dbToDisplay)로 변환해 반환하므로 기대값도 동일 변환한다.
        AiUsageFailureResponse expected = new AiUsageFailureResponse(
                "JOB_ANALYSIS", "OpenAI down", BDisplayTime.dbToDisplay(createdAt));
        assertThat(response).containsExactly(expected);
        verify(applicationCaseMapper).findApplicationCaseByIdAndUserId(10L, 1L);
        verify(applicationCaseMapper).findBFailureLogsByCaseId(10L, 5);
    }

    @Test
    void getActiveExtractionJobsScopesLookupToCurrentUser() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                mock(JobPostingService.class), mock(OpenAiResponsesClient.class));
        ApplicationCaseExtraction queued = extraction(30L, 10L, 20L, 1L, "TEXT", "QUEUED");

        when(extractionMapper.findActiveExtractionsByUserId(1L)).thenReturn(List.of(queued));

        List<ApplicationCaseExtractionResponse> response = service.getActiveExtractions(1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(30L);
        assertThat(response.get(0).status()).isEqualTo("QUEUED");
        verify(extractionMapper).findActiveExtractionsByUserId(1L);
    }

    @Test
    void getLatestExtractionJobRequiresOwnedApplicationCaseBeforeLookup() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                mock(JobPostingService.class), mock(OpenAiResponsesClient.class));
        ApplicationCaseExtraction latest = extraction(31L, 10L, 20L, 1L, "URL", "SUCCEEDED");

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .build());
        when(extractionMapper.findLatestExtractionByApplicationCaseId(10L)).thenReturn(latest);

        ApplicationCaseExtractionResponse response = service.getLatestJobPostingExtraction(1L, 10L);

        assertThat(response.id()).isEqualTo(31L);
        assertThat(response.sourceType()).isEqualTo("URL");
        verify(applicationCaseMapper).findApplicationCaseByIdAndUserId(10L, 1L);
        verify(extractionMapper).findLatestExtractionByApplicationCaseId(10L);
    }

    @Test
    void getLatestExtractionJobThrowsNotFoundWhenOwnedCaseHasNoExtraction() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                mock(JobPostingService.class), mock(OpenAiResponsesClient.class));

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .build());

        assertThatThrownBy(() -> service.getLatestJobPostingExtraction(1L, 10L))
                .isInstanceOf(BusinessException.class);
        verify(applicationCaseMapper).findApplicationCaseByIdAndUserId(10L, 1L);
        verify(extractionMapper).findLatestExtractionByApplicationCaseId(10L);
    }

    @Test
    void reviewJobPostingExtractionSavesReviewedRevisionAndResetsQualityMetadata() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, mock(OpenAiResponsesClient.class), notificationMapper, autoPipelineService);
        ApplicationCaseExtraction reviewRequired = ApplicationCaseExtraction.builder()
                .id(32L)
                .applicationCaseId(10L)
                .jobPostingId(20L)
                .userId(1L)
                .sourceType("IMAGE")
                .status("SUCCEEDED")
                .qualityStatus("REVIEW_REQUIRED")
                .qualityScore(45)
                .fallbackEligible(true)
                .fallbackReason("ocr_low_confidence")
                .build();
        ApplicationCaseExtraction reviewed = ApplicationCaseExtraction.builder()
                .id(32L)
                .applicationCaseId(10L)
                .jobPostingId(44L)
                .userId(1L)
                .sourceType("IMAGE")
                .status("SUCCEEDED")
                .qualityStatus("PASS")
                .qualityScore(100)
                .fallbackEligible(false)
                .build();

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .build());
        when(extractionMapper.findLatestExtractionByApplicationCaseId(10L)).thenReturn(reviewRequired, reviewed);
        when(jobPostingService.saveJobPosting(eq(1L), eq(10L), any(JobPostingRequest.class)))
                .thenReturn(new JobPostingResponse(44L, 10L, 3, "Reviewed posting text", null, "Reviewed posting text", "MANUAL", null));
        when(extractionMapper.markExtractionReviewed(eq(32L), eq(44L), eq(100), any(), any())).thenReturn(1);

        ApplicationCaseExtractionResponse response = service.reviewJobPostingExtraction(
                1L,
                10L,
                new ReviewJobPostingExtractionRequest("Reviewed posting text"));

        assertThat(response.jobPostingId()).isEqualTo(44L);
        assertThat(response.qualityStatus()).isEqualTo("PASS");
        assertThat(response.qualityScore()).isEqualTo(100);
        assertThat(response.fallbackEligible()).isFalse();
        ArgumentCaptor<JobPostingRequest> postingRequestCaptor = ArgumentCaptor.forClass(JobPostingRequest.class);
        verify(jobPostingService).saveJobPosting(eq(1L), eq(10L), postingRequestCaptor.capture());
        assertThat(postingRequestCaptor.getValue().sourceType()).isEqualTo("MANUAL");
        assertThat(postingRequestCaptor.getValue().extractedText()).isEqualTo("Reviewed posting text");
        ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> modelVersionsCaptor = ArgumentCaptor.forClass(String.class);
        verify(extractionMapper).markExtractionReviewed(
                eq(32L),
                eq(44L),
                eq(100),
                reportCaptor.capture(),
                modelVersionsCaptor.capture());
        verify(notificationMapper).markTypeAsReadByTarget(
                1L,
                "JOB_POSTING_EXTRACTION_REVIEW_REQUIRED",
                "APPLICATION_CASE",
                10L);
        verify(autoPipelineService).runAfterExtractionPass(
                1L,
                10L,
                44L,
                3,
                "Reviewed posting text");
        assertThat(reportCaptor.getValue()).contains("\"reviewed\":true");
        assertThat(modelVersionsCaptor.getValue()).contains("user-confirmed-v1");
    }

    @Test
    void confirmEditedPostingOnPassExtractionSavesRevisionAndRunsPipelineOnce() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, mock(OpenAiResponsesClient.class), notificationMapper, autoPipelineService);
        ApplicationCaseExtraction passed = ApplicationCaseExtraction.builder()
                .id(50L)
                .applicationCaseId(10L)
                .jobPostingId(20L)
                .userId(1L)
                .sourceType("IMAGE")
                .status("SUCCEEDED")
                .qualityStatus("PASS")
                .qualityScore(90)
                .fallbackEligible(false)
                .build();
        ApplicationCaseExtraction confirmed = ApplicationCaseExtraction.builder()
                .id(50L)
                .applicationCaseId(10L)
                .jobPostingId(61L)
                .userId(1L)
                .sourceType("IMAGE")
                .status("SUCCEEDED")
                .qualityStatus("PASS")
                .qualityScore(100)
                .fallbackEligible(false)
                .build();

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .build());
        when(extractionMapper.findLatestExtractionByApplicationCaseId(10L)).thenReturn(passed, confirmed);
        when(jobPostingService.saveJobPosting(eq(1L), eq(10L), any(JobPostingRequest.class)))
                .thenReturn(new JobPostingResponse(61L, 10L, 4, "Edited posting text", null, "Edited posting text", "MANUAL", null));
        when(extractionMapper.markExtractionReviewed(eq(50L), eq(61L), eq(100), any(), any())).thenReturn(1);

        ApplicationCaseExtractionResponse response = service.confirmEditedPosting(
                1L,
                10L,
                new ConfirmJobPostingExtractionRequest("Edited posting text"));

        assertThat(response.jobPostingId()).isEqualTo(61L);
        assertThat(response.qualityStatus()).isEqualTo("PASS");
        ArgumentCaptor<JobPostingRequest> postingRequestCaptor = ArgumentCaptor.forClass(JobPostingRequest.class);
        verify(jobPostingService).saveJobPosting(eq(1L), eq(10L), postingRequestCaptor.capture());
        assertThat(postingRequestCaptor.getValue().sourceType()).isEqualTo("MANUAL");
        assertThat(postingRequestCaptor.getValue().extractedText()).isEqualTo("Edited posting text");
        // OCR/추출은 다시 돌리지 않고(분석만), 자동 파이프라인은 정확히 1회 실행한다.
        verify(jobPostingService, never()).saveJobPostingForExtractionQueue(any(), any(), any());
        verify(extractionMapper, never()).insertApplicationCaseExtraction(any());
        verify(autoPipelineService, times(1)).runAfterExtractionPass(1L, 10L, 61L, 4, "Edited posting text");
    }

    @Test
    void confirmEditedPostingAllowsReviewRequiredExtraction() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, mock(OpenAiResponsesClient.class), notificationMapper, autoPipelineService);
        ApplicationCaseExtraction reviewRequired = ApplicationCaseExtraction.builder()
                .id(51L)
                .applicationCaseId(10L)
                .jobPostingId(20L)
                .userId(1L)
                .sourceType("IMAGE")
                .status("SUCCEEDED")
                .qualityStatus("REVIEW_REQUIRED")
                .build();
        ApplicationCaseExtraction confirmed = ApplicationCaseExtraction.builder()
                .id(51L)
                .applicationCaseId(10L)
                .jobPostingId(62L)
                .userId(1L)
                .sourceType("IMAGE")
                .status("SUCCEEDED")
                .qualityStatus("PASS")
                .build();

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .build());
        when(extractionMapper.findLatestExtractionByApplicationCaseId(10L)).thenReturn(reviewRequired, confirmed);
        when(jobPostingService.saveJobPosting(eq(1L), eq(10L), any(JobPostingRequest.class)))
                .thenReturn(new JobPostingResponse(62L, 10L, 2, "Edited posting text", null, "Edited posting text", "MANUAL", null));
        when(extractionMapper.markExtractionReviewed(eq(51L), eq(62L), eq(100), any(), any())).thenReturn(1);

        ApplicationCaseExtractionResponse response = service.confirmEditedPosting(
                1L,
                10L,
                new ConfirmJobPostingExtractionRequest("Edited posting text"));

        assertThat(response.qualityStatus()).isEqualTo("PASS");
        verify(autoPipelineService, times(1)).runAfterExtractionPass(1L, 10L, 62L, 2, "Edited posting text");
    }

    @Test
    void confirmEditedPostingRejectsNonCompletedExtraction() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                jobPostingService, mock(OpenAiResponsesClient.class), notificationMapper, autoPipelineService);
        ApplicationCaseExtraction running = ApplicationCaseExtraction.builder()
                .id(52L)
                .applicationCaseId(10L)
                .userId(1L)
                .sourceType("IMAGE")
                .status("RUNNING")
                .build();

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .build());
        when(extractionMapper.findLatestExtractionByApplicationCaseId(10L)).thenReturn(running);

        assertThatThrownBy(() -> service.confirmEditedPosting(
                1L,
                10L,
                new ConfirmJobPostingExtractionRequest("Edited posting text")))
                .isInstanceOf(BusinessException.class);
        verify(jobPostingService, never()).saveJobPosting(any(), any(), any());
        verify(autoPipelineService, never()).runAfterExtractionPass(any(), any(), any(), any(), any());
    }

    @Test
    void getLatestExtractionJobsReturnsEmptyWithoutMapperLookupWhenIdsAreEmpty() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                mock(JobPostingService.class), mock(OpenAiResponsesClient.class));

        assertThat(service.getLatestJobPostingExtractions(1L, List.of())).isEmpty();
        assertThat(service.getLatestJobPostingExtractions(1L, null)).isEmpty();

        verify(extractionMapper, never()).findLatestExtractionsByApplicationCaseIdsAndUserId(any(), any());
    }

    @Test
    void getLatestExtractionJobsDeduplicatesIdsBeforeBulkLookup() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                mock(JobPostingService.class), mock(OpenAiResponsesClient.class));
        ApplicationCaseExtraction latest = extraction(31L, 10L, 20L, 1L, "URL", "SUCCEEDED");

        when(extractionMapper.findLatestExtractionsByApplicationCaseIdsAndUserId(eq(1L), any()))
                .thenReturn(List.of(latest));

        List<ApplicationCaseExtractionResponse> response = service.getLatestJobPostingExtractions(
                1L,
                List.of(10L, 10L, 11L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(extractionMapper).findLatestExtractionsByApplicationCaseIdsAndUserId(eq(1L), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(10L, 11L);
        assertThat(response).hasSize(1);
        assertThat(response.get(0).applicationCaseId()).isEqualTo(10L);
    }

    @Test
    void getLatestExtractionJobsRejectsNonPositiveIds() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                mock(JobPostingService.class), mock(OpenAiResponsesClient.class));

        assertThatThrownBy(() -> service.getLatestJobPostingExtractions(1L, List.of(10L, 0L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(extractionMapper, never()).findLatestExtractionsByApplicationCaseIdsAndUserId(any(), any());
    }

    @Test
    void getLatestExtractionJobsRejectsMoreThanTwoHundredUniqueIds() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                mock(JobPostingService.class), mock(OpenAiResponsesClient.class));
        List<Long> ids = LongStream.rangeClosed(1L, 201L).boxed().toList();

        assertThatThrownBy(() -> service.getLatestJobPostingExtractions(1L, ids))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(extractionMapper, never()).findLatestExtractionsByApplicationCaseIdsAndUserId(any(), any());
    }

    @Test
    void getLatestExtractionJobsUsesUserScopedBulkMapperAndOmitsMissingRows() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = applicationCaseService(applicationCaseMapper, extractionMapper, accessService,
                mock(JobPostingService.class), mock(OpenAiResponsesClient.class));
        ApplicationCaseExtraction first = extraction(31L, 10L, 20L, 1L, "URL", "SUCCEEDED");
        ApplicationCaseExtraction third = extraction(33L, 12L, 22L, 1L, "PDF", "FAILED");

        when(extractionMapper.findLatestExtractionsByApplicationCaseIdsAndUserId(eq(1L), eq(List.of(10L, 11L, 12L))))
                .thenReturn(List.of(first, third));

        List<ApplicationCaseExtractionResponse> response = service.getLatestJobPostingExtractions(
                1L,
                List.of(10L, 11L, 12L));

        verify(extractionMapper).findLatestExtractionsByApplicationCaseIdsAndUserId(1L, List.of(10L, 11L, 12L));
        assertThat(response).extracting(ApplicationCaseExtractionResponse::applicationCaseId)
                .containsExactly(10L, 12L);
    }

    @Test
    void retryJobPostingExtractionDelegatesToStrictReextraction() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingReextractionService reextractionService = mock(JobPostingReextractionService.class);
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = new ApplicationCaseServiceImpl(
                applicationCaseMapper,
                mock(ApplicationCaseExtractionMapper.class),
                accessService,
                mock(JobPostingService.class),
                mock(JobAnalysisService.class),
                mock(CompanyAnalysisService.class),
                mock(JobAnalysisMapper.class),
                mock(OpenAiResponsesClient.class),
                mock(NotificationMapper.class),
                mock(ApplicationCaseAutoPipelineService.class),
                mock(ApplicationCaseInitialRunMapper.class),
                reextractionService);
        ApplicationCaseExtractionResponse expected =
                ApplicationCaseExtractionResponse.from(extraction(41L, 10L, 20L, 1L, "PDF", "QUEUED"));
        when(reextractionService.reextract(1L, 10L, "CLAUDE")).thenReturn(expected);

        ApplicationCaseExtractionResponse response = service.retryJobPostingExtraction(1L, 10L, "CLAUDE");

        // 수동 재추출은 전용 strict 서비스에 위임된다. 진입검증·짧은 TX·strict OCR·상태 전이는
        // JobPostingReextractionServiceTest 가 검증한다(초기 실행 프로필 reopen 은 폐지 — 여기서도 미주입).
        assertThat(response).isSameAs(expected);
        verify(reextractionService).reextract(1L, 10L, "CLAUDE");
    }

    private static ApplicationCaseServiceImpl applicationCaseService(ApplicationCaseMapper applicationCaseMapper,
                                                                     ApplicationCaseAccessService accessService) {
        return applicationCaseService(
                applicationCaseMapper,
                mock(ApplicationCaseExtractionMapper.class),
                accessService,
                mock(JobPostingService.class),
                mock(OpenAiResponsesClient.class));
    }

    private static ApplicationCaseServiceImpl applicationCaseService(ApplicationCaseMapper applicationCaseMapper,
                                                                     ApplicationCaseAccessService accessService,
                                                                     JobPostingService jobPostingService,
                                                                     OpenAiResponsesClient openAiClient) {
        return applicationCaseService(
                applicationCaseMapper,
                mock(ApplicationCaseExtractionMapper.class),
                accessService,
                jobPostingService,
                openAiClient);
    }

    private static ApplicationCaseServiceImpl applicationCaseService(ApplicationCaseMapper applicationCaseMapper,
                                                                     ApplicationCaseExtractionMapper extractionMapper,
                                                                     ApplicationCaseAccessService accessService,
                                                                     JobPostingService jobPostingService,
                                                                     OpenAiResponsesClient openAiClient) {
        return applicationCaseService(
                applicationCaseMapper,
                extractionMapper,
                accessService,
                jobPostingService,
                openAiClient,
                mock(NotificationMapper.class));
    }

    private static ApplicationCaseServiceImpl applicationCaseService(ApplicationCaseMapper applicationCaseMapper,
                                                                     ApplicationCaseExtractionMapper extractionMapper,
                                                                     ApplicationCaseAccessService accessService,
                                                                     JobPostingService jobPostingService,
                                                                     OpenAiResponsesClient openAiClient,
                                                                     NotificationMapper notificationMapper) {
        return applicationCaseService(
                applicationCaseMapper,
                extractionMapper,
                accessService,
                jobPostingService,
                openAiClient,
                notificationMapper,
                mock(ApplicationCaseAutoPipelineService.class));
    }

    @Test
    void createFromJobPostingCreatesPendingInitialRunProfileWithNormalizedProviders() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseAccessService accessService =
                new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = new ApplicationCaseServiceImpl(
                applicationCaseMapper, extractionMapper, accessService, jobPostingService,
                mock(JobAnalysisService.class), mock(CompanyAnalysisService.class), mock(JobAnalysisMapper.class),
                mock(OpenAiResponsesClient.class), notificationMapper, autoPipelineService, initialRunMapper,
                mock(JobPostingReextractionService.class));

        doAnswer(invocation -> {
            ((ApplicationCase) invocation.getArgument(0)).setId(10L);
            return null;
        }).when(applicationCaseMapper).insertApplicationCase(any(ApplicationCase.class));
        doAnswer(invocation -> {
            ((ApplicationCaseExtraction) invocation.getArgument(0)).setId(30L);
            return null;
        }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(
                ApplicationCase.builder().id(10L).userId(1L).sourceType("TEXT").status("DRAFT").build());
        when(jobPostingService.saveJobPostingForExtractionQueue(eq(1L), eq(10L), any(JobPostingRequest.class)))
                .thenReturn(new JobPostingResponse(20L, 10L, 1, "Acme backend role", null, null, "TEXT",
                        LocalDateTime.now()));

        // 선택값을 대/소문자 섞어 전달 → 정규화(대문자) + 알려진 값만 유지되는지 검증.
        service.createFromJobPosting(1L, new CreateApplicationCaseFromJobPostingRequest(
                "Acme backend role", null, null, "TEXT", true, "local", "OpenAI"));

        ArgumentCaptor<ApplicationCaseInitialRun> captor = ArgumentCaptor.forClass(ApplicationCaseInitialRun.class);
        verify(initialRunMapper).insertPending(captor.capture());
        assertThat(captor.getValue().getApplicationCaseId()).isEqualTo(10L);
        assertThat(captor.getValue().getJobAnalysisProvider()).isEqualTo("LOCAL");
        assertThat(captor.getValue().getCompanyAnalysisProvider()).isEqualTo("OPENAI");
    }

    @Test
    void createFromJobPostingRejectsUnknownProviderSelection() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseAccessService accessService =
                new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = new ApplicationCaseServiceImpl(
                applicationCaseMapper, extractionMapper, accessService, jobPostingService,
                mock(JobAnalysisService.class), mock(CompanyAnalysisService.class), mock(JobAnalysisMapper.class),
                mock(OpenAiResponsesClient.class), mock(NotificationMapper.class),
                mock(ApplicationCaseAutoPipelineService.class), initialRunMapper,
                mock(JobPostingReextractionService.class));

        doAnswer(invocation -> {
            ((ApplicationCase) invocation.getArgument(0)).setId(11L);
            return null;
        }).when(applicationCaseMapper).insertApplicationCase(any(ApplicationCase.class));
        doAnswer(invocation -> {
            ((ApplicationCaseExtraction) invocation.getArgument(0)).setId(31L);
            return null;
        }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(11L, 1L)).thenReturn(
                ApplicationCase.builder().id(11L).userId(1L).sourceType("TEXT").status("DRAFT").build());
        when(jobPostingService.saveJobPostingForExtractionQueue(eq(1L), eq(11L), any(JobPostingRequest.class)))
                .thenReturn(new JobPostingResponse(21L, 11L, 1, "role", null, null, "TEXT", LocalDateTime.now()));

        // 비어있지 않은 알 수 없는 provider = 유효하지 않은 명시 선택 → 조용히 null 로 바꾸지 않고 400 으로 거절.
        assertThatThrownBy(() -> service.createFromJobPosting(1L, new CreateApplicationCaseFromJobPostingRequest(
                "role", null, null, "TEXT", false, "gpt-9", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        // 잘못된 provider는 케이스·공고·추출 큐·프로필 이전에 거절 → 아무 부수효과 없음.
        verify(applicationCaseMapper, never()).insertApplicationCase(any());
        verify(jobPostingService, never()).saveJobPostingForExtractionQueue(any(), any(), any());
        verify(extractionMapper, never()).insertApplicationCaseExtraction(any());
        verify(initialRunMapper, never()).insertPending(any());
    }

    @Test
    void createFromJobPostingUploadRejectsUnknownProviderBeforeStoringFile() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseAccessService accessService =
                new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = new ApplicationCaseServiceImpl(
                applicationCaseMapper, extractionMapper, accessService, jobPostingService,
                mock(JobAnalysisService.class), mock(CompanyAnalysisService.class), mock(JobAnalysisMapper.class),
                mock(OpenAiResponsesClient.class), mock(NotificationMapper.class),
                mock(ApplicationCaseAutoPipelineService.class), initialRunMapper,
                mock(JobPostingReextractionService.class));
        MultipartFile file = mock(MultipartFile.class);

        assertThatThrownBy(() -> service.createFromJobPostingUpload(1L, file, "PDF", true, "gpt-9", null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        // 파일 저장(saveUploadedJobPostingReferenceForNewCase) 이전에 거절 → orphan 파일·행 없음.
        verify(applicationCaseMapper, never()).insertApplicationCase(any());
        verify(jobPostingService, never()).saveUploadedJobPostingReferenceForNewCase(any(), any(), any(), any());
        verify(extractionMapper, never()).insertApplicationCaseExtraction(any());
        verify(initialRunMapper, never()).insertPending(any());
    }

    @Test
    void createFromJobPostingUploadRejectsUnknownOcrProviderBeforeStoringFile() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseAccessService accessService =
                new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        ApplicationCaseServiceImpl service = new ApplicationCaseServiceImpl(
                applicationCaseMapper, extractionMapper, accessService, jobPostingService,
                mock(JobAnalysisService.class), mock(CompanyAnalysisService.class), mock(JobAnalysisMapper.class),
                mock(OpenAiResponsesClient.class), mock(NotificationMapper.class),
                mock(ApplicationCaseAutoPipelineService.class), initialRunMapper,
                mock(JobPostingReextractionService.class));
        MultipartFile file = mock(MultipartFile.class);

        // LOCAL 은 OCR 대상이 아니므로 OCR provider 로는 거절돼야 한다(분석 provider 집합과 다름).
        assertThatThrownBy(() -> service.createFromJobPostingUpload(1L, file, "PDF", true, null, null, "local"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(applicationCaseMapper, never()).insertApplicationCase(any());
        verify(jobPostingService, never()).saveUploadedJobPostingReferenceForNewCase(any(), any(), any(), any());
        verify(extractionMapper, never()).insertApplicationCaseExtraction(any());
        verify(initialRunMapper, never()).insertPending(any());
    }

    private static ApplicationCaseServiceImpl applicationCaseService(ApplicationCaseMapper applicationCaseMapper,
                                                                     ApplicationCaseExtractionMapper extractionMapper,
                                                                     ApplicationCaseAccessService accessService,
                                                                     JobPostingService jobPostingService,
                                                                     OpenAiResponsesClient openAiClient,
                                                                     NotificationMapper notificationMapper,
                                                                     ApplicationCaseAutoPipelineService autoPipelineService) {
        return new ApplicationCaseServiceImpl(
                applicationCaseMapper,
                extractionMapper,
                accessService,
                jobPostingService,
                mock(JobAnalysisService.class),
                mock(CompanyAnalysisService.class),
                mock(JobAnalysisMapper.class),
                openAiClient,
                notificationMapper,
                autoPipelineService,
                mock(ApplicationCaseInitialRunMapper.class),
                mock(JobPostingReextractionService.class));
    }

    private static ApplicationCaseExtraction extraction(Long id,
                                                        Long applicationCaseId,
                                                        Long jobPostingId,
                                                        Long userId,
                                                        String sourceType,
                                                        String status) {
        return ApplicationCaseExtraction.builder()
                .id(id)
                .applicationCaseId(applicationCaseId)
                .jobPostingId(jobPostingId)
                .userId(userId)
                .sourceType(sourceType)
                .status(status)
                .build();
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

    private static com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer companyAnalysisCanonicalizer() {
        return new com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer(new ObjectMapper());
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
                "[{\"fact\":\"job posting mentions B2B platform\",\"source\":\"job posting\","
                        + "\"evidence\":\"Backend platform job posting\"}]",
                "[{\"inference\":\"platform operations may be discussed\",\"basis\":\"job posting mentions B2B platform\"}]",
                "[]",
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
