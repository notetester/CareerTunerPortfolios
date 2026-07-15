package com.careertuner.ai.autoprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.ai.autoprep.mapper.AutoPrepCaseDedupeMapper;
import com.careertuner.applicationcase.dto.ApplicationCaseFromJobPostingResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.service.ApplicationCaseService;

class AutoPrepCaseCreationServiceTest {

    private final AutoPrepCaseDedupeMapper dedupeMapper = mock(AutoPrepCaseDedupeMapper.class);
    private final AutoPrepAttachmentLoader attachmentLoader = mock(AutoPrepAttachmentLoader.class);
    private final ApplicationCaseService applicationCaseService = mock(ApplicationCaseService.class);
    private final AutoPrepCaseCreationService service = new AutoPrepCaseCreationService(
            dedupeMapper, attachmentLoader, applicationCaseService);

    @Test
    void createOrReuse_reusesDurableMappingWithoutReloadingDeletedFile() {
        when(dedupeMapper.insertReservation(1L, 55L)).thenReturn(0);
        when(dedupeMapper.findApplicationCaseIdForUpdate(1L, 55L)).thenReturn(42L);

        Long result = service.createOrReuse(1L, List.of(55L), List.of(66L));

        assertThat(result).isEqualTo(42L);
        verifyNoInteractions(attachmentLoader, applicationCaseService);
        verify(dedupeMapper, never()).bindApplicationCase(any(), any(), any());
    }

    @Test
    void createOrReuse_createsAndBindsOnlyForReservationWinner() {
        when(dedupeMapper.insertReservation(1L, 55L)).thenReturn(1);
        when(attachmentLoader.loadForRequest(1L, List.of(55L), List.of(55L), List.of(66L)))
                .thenReturn(List.of(new PrepAttachment(
                        55L, "jd.txt", "text/plain", 100L, "카카오 프론트엔드 신입 채용")));
        when(applicationCaseService.createFromJobPosting(eq(1L), any()))
                .thenReturn(createdCase(42L));
        when(dedupeMapper.bindApplicationCase(1L, 55L, 42L)).thenReturn(1);

        Long result = service.createOrReuse(1L, List.of(55L), List.of(66L));

        assertThat(result).isEqualTo(42L);
        verify(applicationCaseService).createFromJobPosting(eq(1L), any());
        verify(dedupeMapper).bindApplicationCase(1L, 55L, 42L);
    }

    @Test
    void createOrReuse_releasesReservationWhenAttachmentHasNoText() {
        when(dedupeMapper.insertReservation(1L, 55L)).thenReturn(1);
        when(attachmentLoader.loadForRequest(1L, List.of(55L), List.of(55L), null))
                .thenReturn(List.of(new PrepAttachment(55L, "scan.pdf", "application/pdf", 100L, null)));
        when(dedupeMapper.deleteUnboundReservation(1L, 55L)).thenReturn(1);

        Long result = service.createOrReuse(1L, List.of(55L), null);

        assertThat(result).isNull();
        verify(dedupeMapper).deleteUnboundReservation(1L, 55L);
        verifyNoInteractions(applicationCaseService);
    }

    @Test
    void createOrReuse_failsTransactionWhenCreatedCaseCannotBeBound() {
        when(dedupeMapper.insertReservation(1L, 55L)).thenReturn(1);
        when(attachmentLoader.loadForRequest(1L, List.of(55L), List.of(55L), null))
                .thenReturn(List.of(new PrepAttachment(55L, "jd.txt", "text/plain", 10L, "채용 공고")));
        when(applicationCaseService.createFromJobPosting(eq(1L), any())).thenReturn(createdCase(42L));
        when(dedupeMapper.bindApplicationCase(1L, 55L, 42L)).thenReturn(0);

        assertThatThrownBy(() -> service.createOrReuse(1L, List.of(55L), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("연결하지 못했습니다");
    }

    @Test
    void createOrReuse_ownsOneTransactionAcrossReservationCreationAndBinding() throws Exception {
        Transactional transactional = AutoPrepCaseCreationService.class
                .getMethod("createOrReuse", Long.class, List.class, List.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    void createOrReuseUpload_validatesPendingKeyAndCreatesExactlyOnce() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "job.pdf", "application/pdf", new byte[] { 1, 2, 3 });
        when(dedupeMapper.insertReservation(1L, 77L)).thenReturn(1);
        when(applicationCaseService.createFromJobPostingUpload(
                1L, file, "PDF", Boolean.FALSE, null, null, null)).thenReturn(createdCase(81L));
        when(dedupeMapper.bindApplicationCase(1L, 77L, 81L)).thenReturn(1);

        Long result = service.createOrReuseUpload(1L, 77L, file, "PDF");

        assertThat(result).isEqualTo(81L);
        verify(attachmentLoader).validatePendingAutoPrepFile(1L, 77L);
        verify(dedupeMapper).bindApplicationCase(1L, 77L, 81L);
    }

    @Test
    void createOrReuseUpload_responseLossRetryReusesCaseForSamePendingFileId() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "job.pdf", "application/pdf", new byte[] { 1, 2, 3 });
        when(dedupeMapper.insertReservation(1L, 77L)).thenReturn(0);
        when(dedupeMapper.findApplicationCaseIdForUpdate(1L, 77L)).thenReturn(81L);

        Long result = service.createOrReuseUpload(1L, 77L, file, "PDF");

        assertThat(result).isEqualTo(81L);
        verify(attachmentLoader).validatePendingAutoPrepFile(1L, 77L);
        verify(applicationCaseService, never()).createFromJobPostingUpload(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createOrReuse_concurrentRequestsCreateExactlyOneCase() throws Exception {
        BlockingReservationMapper mapper = new BlockingReservationMapper();
        AutoPrepAttachmentLoader loader = mock(AutoPrepAttachmentLoader.class);
        ApplicationCaseService cases = mock(ApplicationCaseService.class);
        AutoPrepCaseCreationService concurrentService = new AutoPrepCaseCreationService(mapper, loader, cases);
        when(loader.loadForRequest(1L, List.of(55L), List.of(55L), null))
                .thenReturn(List.of(new PrepAttachment(55L, "jd.txt", "text/plain", 10L, "채용 공고")));
        AtomicInteger creations = new AtomicInteger();
        when(cases.createFromJobPosting(eq(1L), any())).thenAnswer(invocation -> {
            creations.incrementAndGet();
            return createdCase(42L);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Long> first = executor.submit(() -> {
                start.await();
                return concurrentService.createOrReuse(1L, List.of(55L), null);
            });
            Future<Long> second = executor.submit(() -> {
                start.await();
                return concurrentService.createOrReuse(1L, List.of(55L), null);
            });
            start.countDown();

            assertThat(first.get(3, TimeUnit.SECONDS)).isEqualTo(42L);
            assertThat(second.get(3, TimeUnit.SECONDS)).isEqualTo(42L);
            assertThat(creations).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static ApplicationCaseFromJobPostingResponse createdCase(Long id) {
        ApplicationCaseResponse applicationCase = new ApplicationCaseResponse(
                id, CaseSlotValidator.PLACEHOLDER_COMPANY, CaseSlotValidator.PLACEHOLDER_JOB_TITLE,
                null, null, "TEXT", "DRAFT", false, false, null, null, null, null);
        return new ApplicationCaseFromJobPostingResponse(applicationCase, null, null, null);
    }

    /** MySQL INSERT IGNORE 유니크 키 대기 후 확정 매핑 조회 동작을 단위 테스트에서 재현한다. */
    private static final class BlockingReservationMapper implements AutoPrepCaseDedupeMapper {
        private final AtomicBoolean reserved = new AtomicBoolean();
        private final AtomicReference<Long> applicationCaseId = new AtomicReference<>();
        private final CountDownLatch bindingFinished = new CountDownLatch(1);

        @Override
        public int insertReservation(Long userId, Long fileId) {
            if (reserved.compareAndSet(false, true)) {
                return 1;
            }
            try {
                if (!bindingFinished.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("경쟁 예약 바인드 대기 시간 초과");
                }
                return 0;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public Long findApplicationCaseIdForUpdate(Long userId, Long fileId) {
            return applicationCaseId.get();
        }

        @Override
        public int bindApplicationCase(Long userId, Long fileId, Long caseId) {
            applicationCaseId.set(caseId);
            bindingFinished.countDown();
            return 1;
        }

        @Override
        public int deleteUnboundReservation(Long userId, Long fileId) {
            bindingFinished.countDown();
            return 1;
        }
    }
}
