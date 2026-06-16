package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobPostingMetadataPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.mapper.JobPostingMapper;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.mapper.NotificationMapper;

class ApplicationCaseExtractionWorkerTest {

    @Test
    void processQueuedUrlExtractionClaimsJobSavesExtractedTextUpdatesMetadataAndNotifies() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationMapper);
        ApplicationCaseExtraction extraction = extraction(30L, 10L, 20L, 1L, "URL");
        String jobUrl = "https://example.com/jobs/backend";
        String extractedText = "Acme is hiring a Backend Engineer.";

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(30L)).thenReturn(1);
        when(extractionMapper.findRunningExtractionForUpdate(30L)).thenReturn(extraction);
        when(jobPostingMapper.findJobPostingByIdAndCaseId(20L, 10L)).thenReturn(JobPosting.builder()
                .id(20L)
                .applicationCaseId(10L)
                .revision(1)
                .uploadedFileUrl(jobUrl)
                .sourceType("URL")
                .build());
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(existingCase());
        when(jobPostingService.extractUrlJobPosting(jobUrl)).thenReturn(new ExtractedPosting(
                "URL",
                jobUrl,
                jobUrl,
                extractedText,
                null));
        when(jobPostingService.saveExtractedJobPosting(eq(1L), eq(10L), any(ExtractedPosting.class)))
                .thenReturn(new JobPostingResponse(21L, 10L, 2, jobUrl, jobUrl, extractedText, "URL", null));
        Usage metadataUsage = new Usage("gpt-test", 10, 5, 15);
        when(openAiClient.extractJobPostingMetadata(extractedText)).thenReturn(new JobPostingMetadataPayload(
                "Acme",
                "Backend Engineer",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1),
                metadataUsage));
        when(extractionMapper.markExtractionSucceeded(30L, 21L)).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        InOrder order = inOrder(extractionMapper, jobPostingService, openAiClient);
        order.verify(extractionMapper).claimQueuedExtraction(30L);
        order.verify(jobPostingService).extractUrlJobPosting(jobUrl);
        order.verify(openAiClient).extractJobPostingMetadata(extractedText);

        ArgumentCaptor<ExtractedPosting> extractedCaptor = ArgumentCaptor.forClass(ExtractedPosting.class);
        verify(jobPostingService).saveExtractedJobPosting(eq(1L), eq(10L), extractedCaptor.capture());
        assertThat(extractedCaptor.getValue().extractedText()).isEqualTo(extractedText);

        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        verify(applicationCaseMapper).updateApplicationCase(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getCompanyName()).isEqualTo("Acme");
        assertThat(caseCaptor.getValue().getJobTitle()).isEqualTo("Backend Engineer");
        assertThat(caseCaptor.getValue().getPostingDate()).isNull();
        assertThat(caseCaptor.getValue().getDeadlineDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(caseCaptor.getValue().getStatus()).isEqualTo("DRAFT");

        verify(extractionMapper).markExtractionSucceeded(30L, 21L);
        verify(aiUsageLogService).recordSuccess(1L, 10L, "JOB_POSTING_METADATA", metadataUsage);
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("JOB_POSTING_EXTRACTION_SUCCEEDED");
        assertThat(notificationCaptor.getValue().getTargetType()).isEqualTo("APPLICATION_CASE");
        assertThat(notificationCaptor.getValue().getTargetId()).isEqualTo(10L);
        assertThat(notificationCaptor.getValue().getLink()).isEqualTo("/applications/10/overview");
    }

    @Test
    void processQueuedTextExtractionUsesExistingTextWithoutFetchingOrSavingContent() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationMapper);
        ApplicationCaseExtraction extraction = extraction(31L, 10L, 20L, 1L, "TEXT");
        String originalText = "Original manually pasted posting text.";

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(31L)).thenReturn(1);
        when(extractionMapper.findRunningExtractionForUpdate(31L)).thenReturn(extraction);
        when(jobPostingMapper.findJobPostingByIdAndCaseId(20L, 10L)).thenReturn(JobPosting.builder()
                .id(20L)
                .applicationCaseId(10L)
                .revision(1)
                .originalText(originalText)
                .sourceType("TEXT")
                .build());
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(existingCase());
        when(openAiClient.extractJobPostingMetadata(originalText)).thenReturn(new JobPostingMetadataPayload(
                "",
                "",
                null,
                null,
                null));
        when(extractionMapper.markExtractionSucceeded(31L, 20L)).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        verify(openAiClient).extractJobPostingMetadata(originalText);
        verify(aiUsageLogService, never()).recordSuccess(any(), any(), any(), any());
        verify(jobPostingService, never()).extractUrlJobPosting(any());
        verify(jobPostingService, never()).extractUploadedJobPosting(any(), any(), any(), any());
        verify(jobPostingService, never()).saveExtractedJobPosting(any(), any(), any());

        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        verify(applicationCaseMapper).updateApplicationCase(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getCompanyName()).isEqualTo("Existing Company");
        assertThat(caseCaptor.getValue().getJobTitle()).isEqualTo("Existing Role");
        verify(extractionMapper).markExtractionSucceeded(31L, 20L);
    }

    @Test
    void processQueuedFileExtractionLoadsStoredFileThroughJobPostingService() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationMapper);
        ApplicationCaseExtraction extraction = extraction(32L, 10L, 20L, 1L, "PDF");
        String fileReference = "local:application-postings/10/posting.pdf";
        String extractedText = "PDF extracted posting text.";

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(32L)).thenReturn(1);
        when(extractionMapper.findRunningExtractionForUpdate(32L)).thenReturn(extraction);
        when(jobPostingMapper.findJobPostingByIdAndCaseId(20L, 10L)).thenReturn(JobPosting.builder()
                .id(20L)
                .applicationCaseId(10L)
                .revision(1)
                .uploadedFileUrl(fileReference)
                .sourceType("PDF")
                .build());
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(existingCase());
        when(jobPostingService.extractUploadedJobPosting(1L, 10L, "PDF", fileReference))
                .thenReturn(new ExtractedPosting("PDF", fileReference, null, extractedText, null));
        when(jobPostingService.saveExtractedJobPosting(eq(1L), eq(10L), any(ExtractedPosting.class)))
                .thenReturn(new JobPostingResponse(22L, 10L, 2, null, fileReference, extractedText, "PDF", null));
        when(openAiClient.extractJobPostingMetadata(extractedText)).thenReturn(new JobPostingMetadataPayload(
                "PDF Company",
                "Data Engineer",
                null,
                null,
                null));
        when(extractionMapper.markExtractionSucceeded(32L, 22L)).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        verify(jobPostingService).extractUploadedJobPosting(1L, 10L, "PDF", fileReference);
        verify(extractionMapper).markExtractionSucceeded(32L, 22L);
    }

    @Test
    void processQueuedExtractionMarksFailedAndNotifiesWhenWorkerThrows() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationMapper);
        ApplicationCaseExtraction extraction = extraction(33L, 10L, 20L, 1L, "URL");
        String jobUrl = "https://example.com/jobs/backend";
        String longReason = "URL fetch failed ".repeat(100);

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(33L)).thenReturn(1);
        when(jobPostingMapper.findJobPostingByIdAndCaseId(20L, 10L)).thenReturn(JobPosting.builder()
                .id(20L)
                .applicationCaseId(10L)
                .revision(1)
                .uploadedFileUrl(jobUrl)
                .sourceType("URL")
                .build());
        when(jobPostingService.extractUrlJobPosting(jobUrl)).thenThrow(new IllegalStateException(longReason));
        when(extractionMapper.markExtractionFailed(eq(33L), any(String.class))).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(extractionMapper).markExtractionFailed(eq(33L), reasonCaptor.capture());
        assertThat(reasonCaptor.getValue()).startsWith("URL fetch failed");
        assertThat(reasonCaptor.getValue()).hasSizeLessThanOrEqualTo(1000);
        verify(extractionMapper, never()).markExtractionSucceeded(any(), any());
        verify(applicationCaseMapper, never()).updateApplicationCase(any());
        verify(aiUsageLogService, never()).recordFailure(any(), any(), eq("JOB_POSTING_METADATA"), any());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("JOB_POSTING_EXTRACTION_FAILED");
        assertThat(notificationCaptor.getValue().getLink()).isEqualTo("/applications/10/overview");
    }

    @Test
    void processQueuedExtractionSkipsSuccessSideEffectsWhenTerminalTransitionLosesRace() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationMapper);
        ApplicationCaseExtraction extraction = extraction(37L, 10L, 20L, 1L, "URL");
        String jobUrl = "https://example.com/jobs/backend";
        String extractedText = "Acme is hiring a Backend Engineer.";

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(37L)).thenReturn(1);
        when(jobPostingMapper.findJobPostingByIdAndCaseId(20L, 10L)).thenReturn(JobPosting.builder()
                .id(20L)
                .applicationCaseId(10L)
                .revision(1)
                .uploadedFileUrl(jobUrl)
                .sourceType("URL")
                .build());
        when(jobPostingService.extractUrlJobPosting(jobUrl)).thenReturn(new ExtractedPosting(
                "URL",
                jobUrl,
                jobUrl,
                extractedText,
                null));
        when(openAiClient.extractJobPostingMetadata(extractedText)).thenReturn(new JobPostingMetadataPayload(
                "Acme",
                "Backend Engineer",
                null,
                null,
                null));
        when(extractionMapper.findRunningExtractionForUpdate(37L)).thenReturn(null);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        verify(jobPostingService, never()).saveExtractedJobPosting(any(), any(), any());
        verify(applicationCaseMapper, never()).updateApplicationCase(any());
        verify(aiUsageLogService, never()).recordSuccess(any(), any(), any(), any());
        verify(notificationMapper, never()).insert(any());
    }

    @Test
    void processQueuedExtractionRecordsMetadataFailureWhenOpenAiMetadataFails() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationMapper);
        ApplicationCaseExtraction extraction = extraction(38L, 10L, 20L, 1L, "TEXT");
        String originalText = "Original manually pasted posting text.";

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(38L)).thenReturn(1);
        when(jobPostingMapper.findJobPostingByIdAndCaseId(20L, 10L)).thenReturn(JobPosting.builder()
                .id(20L)
                .applicationCaseId(10L)
                .revision(1)
                .originalText(originalText)
                .sourceType("TEXT")
                .build());
        when(openAiClient.extractJobPostingMetadata(originalText)).thenThrow(new IllegalStateException("metadata down"));
        when(extractionMapper.markExtractionFailed(eq(38L), any(String.class))).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        verify(aiUsageLogService).recordFailure(1L, 10L, "JOB_POSTING_METADATA", "metadata down");
        verify(extractionMapper).markExtractionFailed(eq(38L), any(String.class));
        verify(applicationCaseMapper, never()).updateApplicationCase(any());
    }

    @Test
    void processQueuedExtractionSkipsWorkWhenClaimLosesRace() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationMapper);
        ApplicationCaseExtraction extraction = extraction(34L, 10L, 20L, 1L, "URL");

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(34L)).thenReturn(0);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isZero();
        verify(jobPostingMapper, never()).findJobPostingByIdAndCaseId(any(), any());
        verify(jobPostingService, never()).extractUrlJobPosting(any());
        verify(openAiClient, never()).extractJobPostingMetadata(any());
        verify(notificationMapper, never()).insert(any());
    }

    @Test
    void processQueuedExtractionsMarksStaleRunningJobsFailedAndNotifies() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationMapper);
        ApplicationCaseExtraction stale = ApplicationCaseExtraction.builder()
                .id(35L)
                .applicationCaseId(10L)
                .jobPostingId(20L)
                .userId(1L)
                .sourceType("PDF")
                .status("RUNNING")
                .startedAt(LocalDateTime.now().minusMinutes(45))
                .build();

        when(extractionMapper.findStaleRunningExtractions(any(LocalDateTime.class), eq(5))).thenReturn(List.of(stale));
        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of());
        when(extractionMapper.markExtractionFailed(eq(35L), any(String.class))).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(extractionMapper).markExtractionFailed(eq(35L), reasonCaptor.capture());
        assertThat(reasonCaptor.getValue()).contains("timed out");
        verify(jobPostingMapper, never()).findJobPostingByIdAndCaseId(any(), any());
        verify(jobPostingService, never()).extractUploadedJobPosting(any(), any(), any(), any());
        verify(openAiClient, never()).extractJobPostingMetadata(any());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("JOB_POSTING_EXTRACTION_FAILED");
        assertThat(notificationCaptor.getValue().getTargetId()).isEqualTo(10L);
        assertThat(notificationCaptor.getValue().getLink()).isEqualTo("/applications/10/overview");
    }

    @Test
    void processQueuedExtractionsDoesNotNotifyWhenStaleFailureUpdateLosesRace() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationMapper);
        ApplicationCaseExtraction stale = ApplicationCaseExtraction.builder()
                .id(36L)
                .applicationCaseId(10L)
                .jobPostingId(20L)
                .userId(1L)
                .sourceType("PDF")
                .status("RUNNING")
                .startedAt(LocalDateTime.now().minusMinutes(45))
                .build();

        when(extractionMapper.findStaleRunningExtractions(any(LocalDateTime.class), eq(5))).thenReturn(List.of(stale));
        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of());
        when(extractionMapper.markExtractionFailed(eq(36L), any(String.class))).thenReturn(0);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isZero();
        verify(extractionMapper).markExtractionFailed(eq(36L), any(String.class));
        verify(notificationMapper, never()).insert(any());
    }

    @Test
    void runScheduledDoesNotPropagatePollingFailureToSpringScheduler() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                mock(ApplicationCaseMapper.class),
                mock(JobPostingMapper.class),
                mock(JobPostingService.class),
                mock(OpenAiResponsesClient.class),
                mock(AiUsageLogService.class),
                mock(NotificationMapper.class));

        when(extractionMapper.findQueuedExtractions(5)).thenThrow(new IllegalStateException("table not ready"));

        assertThatCode(worker::runScheduled).doesNotThrowAnyException();
    }

    private static ApplicationCaseExtractionWorker worker(ApplicationCaseExtractionMapper extractionMapper,
                                                          ApplicationCaseMapper applicationCaseMapper,
                                                          JobPostingMapper jobPostingMapper,
                                                          JobPostingService jobPostingService,
                                                          OpenAiResponsesClient openAiClient,
                                                          AiUsageLogService aiUsageLogService,
                                                          NotificationMapper notificationMapper) {
        return new ApplicationCaseExtractionWorker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationMapper,
                transactionTemplate());
    }

    private static ApplicationCaseExtraction extraction(Long id,
                                                        Long applicationCaseId,
                                                        Long jobPostingId,
                                                        Long userId,
                                                        String sourceType) {
        return ApplicationCaseExtraction.builder()
                .id(id)
                .applicationCaseId(applicationCaseId)
                .jobPostingId(jobPostingId)
                .userId(userId)
                .sourceType(sourceType)
                .status("QUEUED")
                .build();
    }

    private static ApplicationCase existingCase() {
        return ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Existing Company")
                .jobTitle("Existing Role")
                .sourceType("TEXT")
                .status("DRAFT")
                .favorite(true)
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
}
