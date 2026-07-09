package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.AiUsage;
import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.dto.JobPostingRequest;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.mapper.JobPostingMapper;
import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;

class JobPostingServiceTest {

    @Test
    void writeOperationsUseReadCommittedIsolationForRevisionRetries() throws NoSuchMethodException {
        Transactional saveTransaction = JobPostingService.class
                .getMethod("saveJobPosting", Long.class, Long.class, JobPostingRequest.class)
                .getAnnotation(Transactional.class);
        Transactional uploadTransaction = JobPostingService.class
                .getMethod("uploadJobPostingFile", Long.class, Long.class, MultipartFile.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(saveTransaction).isNotNull();
        assertThat(saveTransaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(uploadTransaction).isNotNull();
        assertThat(uploadTransaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    @Test
    void saveJobPostingRetriesAfterDuplicateRevisionAndReturnsInsertedPosting() {
        Map<Long, JobPosting> insertedById = new HashMap<>();
        JobPostingMapper jobPostingMapper = mapperReturningInsertedRows(insertedById);
        JobPostingService service = service(jobPostingMapper);
        AtomicInteger insertAttempts = new AtomicInteger();
        List<Integer> insertRevisions = new ArrayList<>();

        when(jobPostingMapper.nextRevisionForCase(10L)).thenReturn(2, 3);
        doAnswer(invocation -> {
            JobPosting posting = invocation.getArgument(0);
            insertRevisions.add(posting.getRevision());
            if (insertAttempts.incrementAndGet() == 1) {
                throw new DuplicateKeyException("duplicate revision");
            }
            posting.setId(41L);
            insertedById.put(41L, copy(posting));
            return null;
        }).when(jobPostingMapper).insertJobPosting(any(JobPosting.class));
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(JobPosting.builder()
                .id(99L)
                .applicationCaseId(10L)
                .revision(99)
                .originalText("unrelated latest")
                .sourceType("TEXT")
                .build());

        JobPostingResponse response = service.saveJobPosting(1L, 10L,
                new JobPostingRequest("updated posting", null, null, "TEXT"));

        verify(jobPostingMapper, times(2)).nextRevisionForCase(10L);
        verify(jobPostingMapper, times(2)).insertJobPosting(any(JobPosting.class));
        assertThat(insertRevisions).containsExactly(2, 3);
        verify(jobPostingMapper, never()).findLatestJobPostingByCaseId(10L);
        assertThat(response.id()).isEqualTo(41L);
        assertThat(response.revision()).isEqualTo(3);
        assertThat(response.originalText()).isEqualTo("updated posting");
    }

    @Test
    void saveJobPostingForExtractionQueueStoresUrlReferenceWithoutExtracting() {
        Map<Long, JobPosting> insertedById = new HashMap<>();
        JobPostingMapper jobPostingMapper = mapperReturningInsertedRows(insertedById);
        JobPostingTextExtractor textExtractor = mock(JobPostingTextExtractor.class);
        JobPostingService service = new JobPostingService(
                new ApplicationCaseAccessService(ownedApplicationCaseMapper(), jobPostingMapper),
                jobPostingMapper,
                mock(AiUsageLogService.class),
                mock(JobPostingFileStorage.class),
                textExtractor);
        String jobUrl = "https://example.com/jobs/backend";

        when(jobPostingMapper.nextRevisionForCase(10L)).thenReturn(2);
        doAnswer(invocation -> {
            JobPosting posting = invocation.getArgument(0);
            posting.setId(42L);
            insertedById.put(42L, copy(posting));
            return null;
        }).when(jobPostingMapper).insertJobPosting(any(JobPosting.class));

        JobPostingResponse response = service.saveJobPostingForExtractionQueue(1L, 10L,
                new JobPostingRequest(null, jobUrl, null, "URL"));

        verify(textExtractor, never()).extractUrl(any());
        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.uploadedFileUrl()).isEqualTo(jobUrl);
        assertThat(response.extractedText()).isNull();
        assertThat(response.sourceType()).isEqualTo("URL");
    }

    @Test
    void uploadJobPostingFileReturnsInsertedPostingInsteadOfLatestPosting() {
        Map<Long, JobPosting> insertedById = new HashMap<>();
        JobPostingMapper jobPostingMapper = mapperReturningInsertedRows(insertedById);
        ApplicationCaseMapper applicationCaseMapper = ownedApplicationCaseMapper();
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        JobPostingFileStorage fileStorage = mock(JobPostingFileStorage.class);
        JobPostingTextExtractor textExtractor = mock(JobPostingTextExtractor.class);
        JobPostingService service = new JobPostingService(
                new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper),
                jobPostingMapper,
                usageLogService,
                fileStorage,
                textExtractor);
        MultipartFile file = mock(MultipartFile.class);
        StoredJobPostingFile storedFile = new StoredJobPostingFile(
                "PDF",
                "local:application-postings/10/posting.pdf",
                "posting.pdf",
                "application/pdf",
                Path.of("posting.pdf"),
                new byte[]{1, 2, 3});

        when(fileStorage.store(10L, file, "PDF")).thenReturn(storedFile);
        when(textExtractor.extractFile(storedFile)).thenReturn(new ExtractedPosting(
                "PDF",
                "local:application-postings/10/posting.pdf",
                null,
                "inserted extracted text",
                null));
        when(jobPostingMapper.nextRevisionForCase(10L)).thenReturn(7);
        doAnswer(invocation -> {
            JobPosting posting = invocation.getArgument(0);
            posting.setId(51L);
            insertedById.put(51L, copy(posting));
            return null;
        }).when(jobPostingMapper).insertJobPosting(any(JobPosting.class));
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(JobPosting.builder()
                .id(999L)
                .applicationCaseId(10L)
                .revision(99)
                .extractedText("another concurrent insert")
                .sourceType("PDF")
                .build());

        JobPostingResponse response = service.uploadJobPostingFile(1L, 10L, file, "PDF");

        verify(jobPostingMapper, never()).findLatestJobPostingByCaseId(10L);
        assertThat(response.id()).isEqualTo(51L);
        assertThat(response.revision()).isEqualTo(7);
        assertThat(response.uploadedFileUrl()).isEqualTo("local:application-postings/10/posting.pdf");
        assertThat(response.extractedText()).isEqualTo("inserted extracted text");
    }

    @Test
    void uploadJobPostingFileRecordsOcrUsageWhenPresent() {
        Map<Long, JobPosting> insertedById = new HashMap<>();
        JobPostingMapper jobPostingMapper = mapperReturningInsertedRows(insertedById);
        ApplicationCaseMapper applicationCaseMapper = ownedApplicationCaseMapper();
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        JobPostingFileStorage fileStorage = mock(JobPostingFileStorage.class);
        JobPostingTextExtractor textExtractor = mock(JobPostingTextExtractor.class);
        JobPostingService service = new JobPostingService(
                new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper),
                jobPostingMapper,
                usageLogService,
                fileStorage,
                textExtractor);
        MultipartFile file = mock(MultipartFile.class);
        StoredJobPostingFile storedFile = new StoredJobPostingFile(
                "PDF",
                "local:application-postings/10/posting.pdf",
                "posting.pdf",
                "application/pdf",
                Path.of("posting.pdf"),
                new byte[]{1, 2, 3});
        AiUsage usage = new AiUsage("claude-haiku-4-5", 1200, 300, 1500);

        when(fileStorage.store(10L, file, "PDF")).thenReturn(storedFile);
        when(textExtractor.extractFile(storedFile)).thenReturn(new ExtractedPosting(
                "PDF",
                "local:application-postings/10/posting.pdf",
                null,
                "claude 로 추출한 공고 텍스트",
                usage,
                "claude",
                "claude-haiku-4-5"));
        when(jobPostingMapper.nextRevisionForCase(10L)).thenReturn(7);
        doAnswer(invocation -> {
            JobPosting posting = invocation.getArgument(0);
            posting.setId(51L);
            insertedById.put(51L, copy(posting));
            return null;
        }).when(jobPostingMapper).insertJobPosting(any(JobPosting.class));

        service.uploadJobPostingFile(1L, 10L, file, "PDF");

        // Claude OCR 도 usage(AiUsage) 를 실어오므로 JOB_POSTING_OCR usage log 로 기록된다(String 반환 시절엔 미기록).
        verify(usageLogService).recordSuccess(1L, 10L, "JOB_POSTING_OCR", usage);
    }

    @Test
    void saveUploadedJobPostingReferenceForNewCaseStoresFileReferenceWithoutOcr() {
        Map<Long, JobPosting> insertedById = new HashMap<>();
        JobPostingMapper jobPostingMapper = mapperReturningInsertedRows(insertedById);
        ApplicationCaseMapper applicationCaseMapper = ownedApplicationCaseMapper();
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        JobPostingFileStorage fileStorage = mock(JobPostingFileStorage.class);
        JobPostingTextExtractor textExtractor = mock(JobPostingTextExtractor.class);
        JobPostingService service = new JobPostingService(
                new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper),
                jobPostingMapper,
                usageLogService,
                fileStorage,
                textExtractor);
        MultipartFile file = mock(MultipartFile.class);
        StoredJobPostingFile storedFile = new StoredJobPostingFile(
                "PDF",
                "local:application-postings/10/posting.pdf",
                "posting.pdf",
                "application/pdf",
                Path.of("posting.pdf"),
                new byte[]{1, 2, 3});

        when(fileStorage.store(10L, file, "PDF")).thenReturn(storedFile);
        when(jobPostingMapper.nextRevisionForCase(10L)).thenReturn(8);
        doAnswer(invocation -> {
            JobPosting posting = invocation.getArgument(0);
            posting.setId(52L);
            insertedById.put(52L, copy(posting));
            return null;
        }).when(jobPostingMapper).insertJobPosting(any(JobPosting.class));

        JobPostingResponse response = service.saveUploadedJobPostingReferenceForNewCase(1L, 10L, file, "PDF");

        verify(textExtractor, never()).extractFile(any());
        verify(usageLogService, never()).recordSuccess(any(), any(), any(), any());
        assertThat(response.id()).isEqualTo(52L);
        assertThat(response.revision()).isEqualTo(8);
        assertThat(response.uploadedFileUrl()).isEqualTo("local:application-postings/10/posting.pdf");
        assertThat(response.extractedText()).isNull();
    }

    @Test
    void uploadJobPostingFileForNewCaseRecordsFailureWithoutUncommittedCaseId() {
        JobPostingMapper jobPostingMapper = mapperReturningInsertedRows(new HashMap<>());
        ApplicationCaseMapper applicationCaseMapper = ownedApplicationCaseMapper();
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        JobPostingFileStorage fileStorage = mock(JobPostingFileStorage.class);
        JobPostingTextExtractor textExtractor = mock(JobPostingTextExtractor.class);
        JobPostingService service = new JobPostingService(
                new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper),
                jobPostingMapper,
                usageLogService,
                fileStorage,
                textExtractor);
        MultipartFile file = mock(MultipartFile.class);
        StoredJobPostingFile storedFile = new StoredJobPostingFile(
                "PDF",
                "local:application-postings/10/posting.pdf",
                "posting.pdf",
                "application/pdf",
                Path.of("posting.pdf"),
                new byte[]{1, 2, 3});
        RuntimeException failure = new IllegalStateException("OCR down");

        when(fileStorage.store(10L, file, "PDF")).thenReturn(storedFile);
        when(textExtractor.extractFile(storedFile)).thenThrow(failure);

        assertThatThrownBy(() -> service.uploadJobPostingFileForNewCase(1L, 10L, file, "PDF"))
                .isSameAs(failure);

        verify(usageLogService).recordFailure(1L, null, "JOB_POSTING_OCR", "OCR down");
    }

    @Test
    void saveJobPostingFailsWithConflictWhenRevisionRetryAttemptsAreExhausted() {
        Map<Long, JobPosting> insertedById = new HashMap<>();
        JobPostingMapper jobPostingMapper = mapperReturningInsertedRows(insertedById);
        JobPostingService service = service(jobPostingMapper);

        when(jobPostingMapper.nextRevisionForCase(10L)).thenReturn(2, 3, 4);
        doThrow(new DuplicateKeyException("duplicate revision"))
                .when(jobPostingMapper)
                .insertJobPosting(any(JobPosting.class));
        when(jobPostingMapper.findLatestJobPostingByCaseId(10L)).thenReturn(JobPosting.builder()
                .id(999L)
                .applicationCaseId(10L)
                .revision(99)
                .originalText("unrelated latest")
                .sourceType("TEXT")
                .build());

        assertThatThrownBy(() -> service.saveJobPosting(1L, 10L,
                new JobPostingRequest("updated posting", null, null, "TEXT")))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("버전 충돌");

        verify(jobPostingMapper, times(3)).nextRevisionForCase(10L);
        verify(jobPostingMapper, times(3)).insertJobPosting(any(JobPosting.class));
        verify(jobPostingMapper, never()).findLatestJobPostingByCaseId(10L);
    }

    private static JobPostingService service(JobPostingMapper jobPostingMapper) {
        return new JobPostingService(
                new ApplicationCaseAccessService(ownedApplicationCaseMapper(), jobPostingMapper),
                jobPostingMapper,
                mock(AiUsageLogService.class),
                mock(JobPostingFileStorage.class),
                mock(JobPostingTextExtractor.class));
    }

    private static ApplicationCaseMapper ownedApplicationCaseMapper() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .build());
        return applicationCaseMapper;
    }

    private static JobPostingMapper mapperReturningInsertedRows(Map<Long, JobPosting> insertedById) {
        return mock(JobPostingMapper.class, invocation -> {
            if ("findJobPostingByIdAndCaseId".equals(invocation.getMethod().getName())) {
                Long id = invocation.getArgument(0);
                Long applicationCaseId = invocation.getArgument(1);
                JobPosting posting = insertedById.get(id);
                if (posting != null && applicationCaseId.equals(posting.getApplicationCaseId())) {
                    return copy(posting);
                }
                return null;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private static JobPosting copy(JobPosting posting) {
        return JobPosting.builder()
                .id(posting.getId())
                .applicationCaseId(posting.getApplicationCaseId())
                .revision(posting.getRevision())
                .originalText(posting.getOriginalText())
                .uploadedFileUrl(posting.getUploadedFileUrl())
                .extractedText(posting.getExtractedText())
                .sourceType(posting.getSourceType())
                .createdAt(posting.getCreatedAt())
                .build();
    }
}
