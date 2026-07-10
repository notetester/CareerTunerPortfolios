package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.mapper.JobPostingMapper;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

class ApplicationCaseExtractionWorkerTest {

    @Test
    void processQueuedUrlExtractionClaimsJobSavesExtractedTextUpdatesMetadataAndNotifies() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                autoPipelineService,
                notificationService);
        ApplicationCaseExtraction extraction = extraction(30L, 10L, 20L, 1L, "URL");
        String jobUrl = "https://example.com/jobs/backend";
        String extractedText = passingPostingText("Acme is hiring a Backend Engineer.");

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
        when(extractionMapper.markExtractionSucceeded(
                eq(30L),
                eq(21L),
                eq("HTML_TEXT"),
                any(),
                eq("PASS"),
                any(),
                any(),
                eq(false),
                any())).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        InOrder order = inOrder(extractionMapper, jobPostingService);
        order.verify(extractionMapper).claimQueuedExtraction(30L);
        order.verify(jobPostingService).extractUrlJobPosting(jobUrl);
        verify(openAiClient, never()).extractJobPostingMetadata(any());

        ArgumentCaptor<ExtractedPosting> extractedCaptor = ArgumentCaptor.forClass(ExtractedPosting.class);
        verify(jobPostingService).saveExtractedJobPosting(eq(1L), eq(10L), extractedCaptor.capture());
        assertThat(extractedCaptor.getValue().extractedText()).isEqualTo(extractedText);

        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        verify(applicationCaseMapper).updateApplicationCase(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getCompanyName()).isEqualTo("Acme");
        assertThat(caseCaptor.getValue().getJobTitle()).isEqualTo("Backend Engineer");
        assertThat(caseCaptor.getValue().getPostingDate()).isNull();
        assertThat(caseCaptor.getValue().getDeadlineDate()).isNull();
        assertThat(caseCaptor.getValue().getStatus()).isEqualTo("DRAFT");

        verify(extractionMapper).markExtractionSucceeded(
                eq(30L),
                eq(21L),
                eq("HTML_TEXT"),
                any(),
                eq("PASS"),
                any(),
                any(),
                eq(false),
                any());
        verify(aiUsageLogService, never()).recordSuccess(any(), any(), any(), any());
        verify(autoPipelineService).runAfterExtractionPass(1L, 10L, 21L, 2, extractedText);
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).notify(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("JOB_POSTING_EXTRACTION_SUCCEEDED");
        assertThat(notificationCaptor.getValue().getTargetType()).isEqualTo("APPLICATION_CASE");
        assertThat(notificationCaptor.getValue().getTargetId()).isEqualTo(10L);
        assertThat(notificationCaptor.getValue().getLink()).isEqualTo("/applications/10/overview");
    }

    @Test
    void jobPostingMetadataLlmFailureKeepsExtractionSucceededAndFallsBackToRegex() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper, applicationCaseMapper, jobPostingMapper, jobPostingService,
                openAiClient, aiUsageLogService, autoPipelineService, notificationService);
        ApplicationCaseExtraction extraction = extraction(30L, 10L, 20L, 1L, "URL");
        String jobUrl = "https://example.com/jobs/backend";
        String extractedText = passingPostingText("Acme is hiring a Backend Engineer.");

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(30L)).thenReturn(1);
        when(extractionMapper.findRunningExtractionForUpdate(30L)).thenReturn(extraction);
        when(jobPostingMapper.findJobPostingByIdAndCaseId(20L, 10L)).thenReturn(JobPosting.builder()
                .id(20L).applicationCaseId(10L).revision(1).uploadedFileUrl(jobUrl).sourceType("URL").build());
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(existingCase());
        when(jobPostingService.extractUrlJobPosting(jobUrl))
                .thenReturn(new ExtractedPosting("URL", jobUrl, jobUrl, extractedText, null));
        when(jobPostingService.saveExtractedJobPosting(eq(1L), eq(10L), any(ExtractedPosting.class)))
                .thenReturn(new JobPostingResponse(21L, 10L, 2, jobUrl, jobUrl, extractedText, "URL", null));
        when(extractionMapper.markExtractionSucceeded(
                eq(30L), eq(21L), eq("HTML_TEXT"), any(), eq("PASS"), any(), any(), eq(false), any())).thenReturn(1);
        // 메타 LLM 이 켜져 있으나 예외 — 부가정보 실패가 추출 성공을 깨면 안 된다(fail-open).
        when(openAiClient.configured()).thenReturn(true);
        when(openAiClient.extractJobPostingMetadata(any())).thenThrow(new RuntimeException("metadata LLM down"));

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        // 추출은 여전히 SUCCEEDED(예외가 extractAndAnalyze 밖으로 새지 않음)
        verify(extractionMapper).markExtractionSucceeded(
                eq(30L), eq(21L), eq("HTML_TEXT"), any(), eq("PASS"), any(), any(), eq(false), any());
        // 메타는 regex 폴백 사용(회사=Acme, 직무=Backend Engineer)
        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        verify(applicationCaseMapper).updateApplicationCase(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getCompanyName()).isEqualTo("Acme");
        assertThat(caseCaptor.getValue().getJobTitle()).isEqualTo("Backend Engineer");
    }

    @Test
    void jobPostingMetadataUsesLlmResultWhenConfiguredAndSuccessful() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper, applicationCaseMapper, jobPostingMapper, jobPostingService,
                openAiClient, aiUsageLogService, autoPipelineService, notificationService);
        ApplicationCaseExtraction extraction = extraction(30L, 10L, 20L, 1L, "URL");
        String jobUrl = "https://example.com/jobs/backend";
        String extractedText = passingPostingText("Acme is hiring a Backend Engineer.");

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(30L)).thenReturn(1);
        when(extractionMapper.findRunningExtractionForUpdate(30L)).thenReturn(extraction);
        when(jobPostingMapper.findJobPostingByIdAndCaseId(20L, 10L)).thenReturn(JobPosting.builder()
                .id(20L).applicationCaseId(10L).revision(1).uploadedFileUrl(jobUrl).sourceType("URL").build());
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(existingCase());
        when(jobPostingService.extractUrlJobPosting(jobUrl))
                .thenReturn(new ExtractedPosting("URL", jobUrl, jobUrl, extractedText, null));
        when(jobPostingService.saveExtractedJobPosting(eq(1L), eq(10L), any(ExtractedPosting.class)))
                .thenReturn(new JobPostingResponse(21L, 10L, 2, jobUrl, jobUrl, extractedText, "URL", null));
        when(extractionMapper.markExtractionSucceeded(
                eq(30L), eq(21L), eq("HTML_TEXT"), any(), eq("PASS"), any(), any(), eq(false), any())).thenReturn(1);
        // 구조화 LLM 메타가 성공하면 regex 보다 우선한다.
        when(openAiClient.configured()).thenReturn(true);
        when(openAiClient.extractJobPostingMetadata(any())).thenReturn(
                new OpenAiResponsesClient.JobPostingMetadataPayload(
                        "엘엘엠 코퍼레이션", "엘엘엠 백엔드 개발자", null, LocalDate.of(2026, 7, 31), null));

        worker.processQueuedExtractions();

        verify(openAiClient).extractJobPostingMetadata(any());
        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        verify(applicationCaseMapper).updateApplicationCase(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getCompanyName()).isEqualTo("엘엘엠 코퍼레이션");
        assertThat(caseCaptor.getValue().getJobTitle()).isEqualTo("엘엘엠 백엔드 개발자");
        assertThat(caseCaptor.getValue().getDeadlineDate()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void processQueuedTextExtractionUsesExistingTextWithoutFetchingOrSavingContent() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                autoPipelineService,
                notificationService);
        ApplicationCaseExtraction extraction = extraction(31L, 10L, 20L, 1L, "TEXT");
        String originalText = passingPostingText("Original manually pasted posting text.");

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
        when(extractionMapper.markExtractionSucceeded(
                eq(31L),
                eq(20L),
                eq("TEXT_DIRECT"),
                any(),
                eq("PASS"),
                any(),
                any(),
                eq(false),
                any())).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        verify(openAiClient, never()).extractJobPostingMetadata(any());
        verify(aiUsageLogService, never()).recordSuccess(any(), any(), any(), any());
        verify(jobPostingService, never()).extractUrlJobPosting(any());
        verify(jobPostingService, never()).extractUploadedJobPosting(any(), any(), any(), any(), any());
        verify(jobPostingService, never()).saveExtractedJobPosting(any(), any(), any());

        ArgumentCaptor<ApplicationCase> caseCaptor = ArgumentCaptor.forClass(ApplicationCase.class);
        verify(applicationCaseMapper).updateApplicationCase(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getCompanyName()).isEqualTo("Acme Corporation is hiring for a product engineering team");
        assertThat(caseCaptor.getValue().getJobTitle()).isEqualTo("Backend Engineer position supporting a commercial SaaS platform");
        verify(extractionMapper).markExtractionSucceeded(
                eq(31L),
                eq(20L),
                eq("TEXT_DIRECT"),
                any(),
                eq("PASS"),
                any(),
                any(),
                eq(false),
                any());
    }

    @Test
    void processQueuedFileExtractionLoadsStoredFileThroughJobPostingService() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                autoPipelineService,
                notificationService);
        ApplicationCaseExtraction extraction = extraction(32L, 10L, 20L, 1L, "PDF");
        String fileReference = "local:application-postings/10/posting.pdf";
        String extractedText = passingPostingText("PDF extracted posting text.");

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
        when(jobPostingService.extractUploadedJobPosting(1L, 10L, "PDF", fileReference, null))
                .thenReturn(new ExtractedPosting("PDF", fileReference, null, extractedText, null));
        when(jobPostingService.saveExtractedJobPosting(eq(1L), eq(10L), any(ExtractedPosting.class)))
                .thenReturn(new JobPostingResponse(22L, 10L, 2, null, fileReference, extractedText, "PDF", null));
        when(extractionMapper.markExtractionSucceeded(
                eq(32L),
                eq(22L),
                eq("PDF_TEXT"),
                any(),
                eq("PASS"),
                any(),
                any(),
                eq(false),
                any())).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        verify(jobPostingService).extractUploadedJobPosting(1L, 10L, "PDF", fileReference, null);
        verify(extractionMapper).markExtractionSucceeded(
                eq(32L),
                eq(22L),
                eq("PDF_TEXT"),
                any(),
                eq("PASS"),
                any(),
                any(),
                eq(false),
                any());
    }

    @Test
    void processQueuedFileExtractionStopsBeforeAnalysisWhenPythonWorkerRequiresReview() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                autoPipelineService,
                notificationService);
        ApplicationCaseExtraction extraction = extraction(39L, 10L, 20L, 1L, "IMAGE");
        extraction.setOcrRequestedProvider("OPENAI"); // 등록 시 고른 OCR provider 가 워커→추출로 이어져야 한다
        String fileReference = "local:application-postings/10/posting.png";
        String extractedText = "Responsibilities: build APIs. Qualifications: Java and Spring.";

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(39L)).thenReturn(1);
        when(extractionMapper.findRunningExtractionForUpdate(39L)).thenReturn(extraction);
        when(jobPostingMapper.findJobPostingByIdAndCaseId(20L, 10L)).thenReturn(JobPosting.builder()
                .id(20L)
                .applicationCaseId(10L)
                .revision(1)
                .uploadedFileUrl(fileReference)
                .sourceType("IMAGE")
                .build());
        when(jobPostingService.extractUploadedJobPosting(1L, 10L, "IMAGE", fileReference, "OPENAI"))
                .thenReturn(new ExtractedPosting(
                        "IMAGE",
                        fileReference,
                        null,
                        extractedText,
                        null,
                        "IMAGE_OCR",
                        55,
                        "REVIEW_REQUIRED",
                        "{\"qualityStatus\":\"REVIEW_REQUIRED\"}",
                        "{\"documentExtractionContract\":\"self_ai_v1\"}",
                        true,
                        "ocr_low_confidence",
                        "worker",
                        null));
        when(jobPostingService.saveExtractedJobPosting(eq(1L), eq(10L), any(ExtractedPosting.class)))
                .thenReturn(new JobPostingResponse(23L, 10L, 2, null, fileReference, extractedText, "IMAGE", null));
        when(extractionMapper.markExtractionSucceeded(
                eq(39L),
                eq(23L),
                eq("IMAGE_OCR"),
                eq(55),
                eq("REVIEW_REQUIRED"),
                any(),
                any(),
                eq(true),
                eq("ocr_low_confidence"))).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        // 등록 시 고른 OCR provider 를 추출 서비스까지 그대로 전달하는지 잠근다(라우팅 배선).
        verify(jobPostingService).extractUploadedJobPosting(1L, 10L, "IMAGE", fileReference, "OPENAI");
        verify(openAiClient, never()).extractJobPostingMetadata(any());
        verify(applicationCaseMapper, never()).updateApplicationCase(any());
        verify(aiUsageLogService, never()).recordSuccess(any(), any(), any(), any());
        verify(autoPipelineService, never()).runAfterExtractionPass(any(), any(), any(), any(), any());
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).notify(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("JOB_POSTING_EXTRACTION_REVIEW_REQUIRED");
    }

    @Test
    void processQueuedFileExtractionFailsWhenPythonWorkerQualityStatusIsUnknown() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationService);
        ApplicationCaseExtraction extraction = extraction(40L, 10L, 20L, 1L, "IMAGE");
        String fileReference = "local:application-postings/10/posting.png";

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(40L)).thenReturn(1);
        when(jobPostingMapper.findJobPostingByIdAndCaseId(20L, 10L)).thenReturn(JobPosting.builder()
                .id(20L)
                .applicationCaseId(10L)
                .revision(1)
                .uploadedFileUrl(fileReference)
                .sourceType("IMAGE")
                .build());
        when(jobPostingService.extractUploadedJobPosting(1L, 10L, "IMAGE", fileReference, null))
                .thenReturn(new ExtractedPosting(
                        "IMAGE",
                        fileReference,
                        null,
                        "Responsibilities: build APIs. Qualifications: Java and Spring.",
                        null,
                        "IMAGE_OCR",
                        75,
                        "AUTO_OK",
                        "{\"qualityStatus\":\"AUTO_OK\"}",
                        "{\"documentExtractionContract\":\"self_ai_v1\"}",
                        false,
                        null,
                        "worker",
                        null));
        when(extractionMapper.markExtractionFailed(
                eq(40L),
                any(String.class),
                eq("IMAGE_OCR"),
                eq(75),
                eq("FAILED"),
                any(),
                any(),
                eq(false),
                any())).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        verify(extractionMapper, never()).markExtractionSucceeded(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                any());
        verify(openAiClient, never()).extractJobPostingMetadata(any());
        verify(applicationCaseMapper, never()).updateApplicationCase(any());
        ArgumentCaptor<String> fallbackReasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(extractionMapper).markExtractionFailed(
                eq(40L),
                any(String.class),
                eq("IMAGE_OCR"),
                eq(75),
                eq("FAILED"),
                any(),
                any(),
                eq(false),
                fallbackReasonCaptor.capture());
        assertThat(fallbackReasonCaptor.getValue()).contains("Invalid qualityStatus");
    }

    @Test
    void processQueuedExtractionMarksFailedAndNotifiesWhenWorkerThrows() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                autoPipelineService,
                notificationService);
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
        when(extractionMapper.markExtractionFailed(
                eq(33L),
                any(String.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(false),
                any())).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(extractionMapper).markExtractionFailed(
                eq(33L),
                reasonCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(false),
                any());
        assertThat(reasonCaptor.getValue()).startsWith("URL fetch failed");
        assertThat(reasonCaptor.getValue()).hasSizeLessThanOrEqualTo(1000);
        verify(extractionMapper, never()).markExtractionSucceeded(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                any());
        verify(applicationCaseMapper, never()).updateApplicationCase(any());
        verify(aiUsageLogService, never()).recordFailure(any(), any(), eq("JOB_POSTING_METADATA"), any());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).notify(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("JOB_POSTING_EXTRACTION_FAILED");
        assertThat(notificationCaptor.getValue().getLink()).isEqualTo("/applications/10/overview");
        // 추출 실패 종결 시 초기 실행 프로필을 닫아(PENDING 누수 방지) 수동 분석 CONFLICT 영구 차단을 막는지 잠근다.
        verify(autoPipelineService).abandonInitialRunIfPending(eq(10L), any(String.class));
    }

    @Test
    void processQueuedExtractionSkipsSuccessSideEffectsWhenTerminalTransitionLosesRace() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationService);
        ApplicationCaseExtraction extraction = extraction(37L, 10L, 20L, 1L, "URL");
        String jobUrl = "https://example.com/jobs/backend";
        String extractedText = passingPostingText("Acme is hiring a Backend Engineer.");

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
        when(extractionMapper.findRunningExtractionForUpdate(37L)).thenReturn(null);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        verify(jobPostingService, never()).saveExtractedJobPosting(any(), any(), any());
        verify(applicationCaseMapper, never()).updateApplicationCase(any());
        verify(aiUsageLogService, never()).recordSuccess(any(), any(), any(), any());
        verify(notificationService, never()).notify(any());
    }

    @Test
    void processQueuedExtractionDoesNotCallOpenAiForMetadataByDefault() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationService);
        ApplicationCaseExtraction extraction = extraction(38L, 10L, 20L, 1L, "TEXT");
        String originalText = passingPostingText("Original manually pasted posting text.");

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(38L)).thenReturn(1);
        when(jobPostingMapper.findJobPostingByIdAndCaseId(20L, 10L)).thenReturn(JobPosting.builder()
                .id(20L)
                .applicationCaseId(10L)
                .revision(1)
                .originalText(originalText)
                .sourceType("TEXT")
                .build());
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(existingCase());
        when(extractionMapper.findRunningExtractionForUpdate(38L)).thenReturn(extraction);
        when(extractionMapper.markExtractionSucceeded(
                eq(38L),
                eq(20L),
                eq("TEXT_DIRECT"),
                any(),
                eq("PASS"),
                any(),
                any(),
                eq(false),
                any())).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        verify(openAiClient, never()).extractJobPostingMetadata(any());
        verify(aiUsageLogService, never()).recordFailure(any(), any(), eq("JOB_POSTING_METADATA"), any());
        verify(extractionMapper).markExtractionSucceeded(
                eq(38L),
                eq(20L),
                eq("TEXT_DIRECT"),
                any(),
                eq("PASS"),
                any(),
                any(),
                eq(false),
                any());
        verify(applicationCaseMapper).updateApplicationCase(any());
    }

    @Test
    void processQueuedExtractionSkipsWorkWhenClaimLosesRace() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                notificationService);
        ApplicationCaseExtraction extraction = extraction(34L, 10L, 20L, 1L, "URL");

        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of(extraction));
        when(extractionMapper.claimQueuedExtraction(34L)).thenReturn(0);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isZero();
        verify(jobPostingMapper, never()).findJobPostingByIdAndCaseId(any(), any());
        verify(jobPostingService, never()).extractUrlJobPosting(any());
        verify(openAiClient, never()).extractJobPostingMetadata(any());
        verify(notificationService, never()).notify(any());
    }

    @Test
    void processQueuedExtractionsMarksStaleRunningJobsFailedAndNotifies() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                autoPipelineService,
                notificationService);
        ApplicationCaseExtraction stale = ApplicationCaseExtraction.builder()
                .id(35L)
                .applicationCaseId(10L)
                .jobPostingId(20L)
                .userId(1L)
                .sourceType("PDF")
                .status("RUNNING")
                .startedAt(LocalDateTime.now().minusMinutes(45))
                .build();

        when(extractionMapper.findStaleRunningExtractions(anyLong(), eq(5))).thenReturn(List.of(stale));
        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of());
        when(extractionMapper.markExtractionFailed(
                eq(35L),
                any(String.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(false),
                any())).thenReturn(1);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(extractionMapper).markExtractionFailed(
                eq(35L),
                reasonCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(false),
                any());
        assertThat(reasonCaptor.getValue()).contains("timed out");
        verify(jobPostingMapper, never()).findJobPostingByIdAndCaseId(any(), any());
        verify(jobPostingService, never()).extractUploadedJobPosting(any(), any(), any(), any(), any());
        verify(openAiClient, never()).extractJobPostingMetadata(any());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).notify(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("JOB_POSTING_EXTRACTION_FAILED");
        assertThat(notificationCaptor.getValue().getTargetId()).isEqualTo(10L);
        assertThat(notificationCaptor.getValue().getLink()).isEqualTo("/applications/10/overview");
        // stale 만료도 추출 실패 종결 — 초기 실행 프로필을 닫아 PENDING 누수를 막는지 잠근다.
        verify(autoPipelineService).abandonInitialRunIfPending(eq(10L), any(String.class));
    }

    @Test
    void processQueuedExtractionsDoesNotNotifyWhenStaleFailureUpdateLosesRace() {
        ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobPostingService jobPostingService = mock(JobPostingService.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService aiUsageLogService = mock(AiUsageLogService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationCaseAutoPipelineService autoPipelineService = mock(ApplicationCaseAutoPipelineService.class);
        ApplicationCaseExtractionWorker worker = worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                autoPipelineService,
                notificationService);
        ApplicationCaseExtraction stale = ApplicationCaseExtraction.builder()
                .id(36L)
                .applicationCaseId(10L)
                .jobPostingId(20L)
                .userId(1L)
                .sourceType("PDF")
                .status("RUNNING")
                .startedAt(LocalDateTime.now().minusMinutes(45))
                .build();

        when(extractionMapper.findStaleRunningExtractions(anyLong(), eq(5))).thenReturn(List.of(stale));
        when(extractionMapper.findQueuedExtractions(5)).thenReturn(List.of());
        when(extractionMapper.markExtractionFailed(
                eq(36L),
                any(String.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(false),
                any())).thenReturn(0);

        int processed = worker.processQueuedExtractions();

        assertThat(processed).isZero();
        verify(extractionMapper).markExtractionFailed(
                eq(36L),
                any(String.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(false),
                any());
        verify(notificationService, never()).notify(any());
        // 실패 마킹 경합에서 진 쪽은 프로필도 건드리지 않는다(이긴 쪽 종결 처리에 맡김).
        verify(autoPipelineService, never()).abandonInitialRunIfPending(any(), any());
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
                mock(NotificationService.class));

        when(extractionMapper.findQueuedExtractions(5)).thenThrow(new IllegalStateException("table not ready"));

        assertThatCode(worker::runScheduled).doesNotThrowAnyException();
    }

    private static ApplicationCaseExtractionWorker worker(ApplicationCaseExtractionMapper extractionMapper,
                                                          ApplicationCaseMapper applicationCaseMapper,
                                                          JobPostingMapper jobPostingMapper,
                                                          JobPostingService jobPostingService,
                                                          OpenAiResponsesClient openAiClient,
                                                          AiUsageLogService aiUsageLogService,
                                                          NotificationService notificationService) {
        return worker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                openAiClient,
                aiUsageLogService,
                mock(ApplicationCaseAutoPipelineService.class),
                notificationService);
    }

    private static ApplicationCaseExtractionWorker worker(ApplicationCaseExtractionMapper extractionMapper,
                                                          ApplicationCaseMapper applicationCaseMapper,
                                                          JobPostingMapper jobPostingMapper,
                                                          JobPostingService jobPostingService,
                                                          OpenAiResponsesClient openAiClient,
                                                          AiUsageLogService aiUsageLogService,
                                                          ApplicationCaseAutoPipelineService autoPipelineService,
                                                          NotificationService notificationService) {
        return new ApplicationCaseExtractionWorker(
                extractionMapper,
                applicationCaseMapper,
                jobPostingMapper,
                jobPostingService,
                new ApplicationCaseExtractionQualityGate(new ObjectMapper()),
                openAiClient,
                aiUsageLogService,
                autoPipelineService,
                notificationService,
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

    private static String passingPostingText(String seed) {
        return """
                %s
                Company: Acme Corporation is hiring for a product engineering team.
                Role: Backend Engineer position supporting a commercial SaaS platform.
                Responsibilities: design APIs, operate Spring services, improve batch workers, and collaborate with frontend engineers.
                Qualifications: Java, Spring Boot, MySQL, REST API design, testing experience, and production debugging.
                Skills: Java Spring MyBatis React TypeScript Python Docker monitoring.
                Employment: full-time role with Seoul hybrid location, benefits, and application deadline.
                Apply: submit resume and portfolio before the deadline.
                """.formatted(seed).repeat(3).trim();
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
