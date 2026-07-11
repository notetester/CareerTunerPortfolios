package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;
import com.careertuner.applicationcase.domain.ApplicationCaseInitialRun;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseInitialRunMapper;
import com.careertuner.applicationcase.service.JobPostingExtractionProcessor.ExtractionResult;
import com.careertuner.applicationcase.service.JobPostingExtractionProcessor.PostFailureAction;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

/**
 * 동기 strict 재추출 오케스트레이션의 계약과 회귀 조건을 잠근다.
 * 저장/mark/REVIEW_REQUIRED 상태 전이 자체(processor)는 워커 테스트가 실제 processor 로 커버하므로
 * 여기서는 processor 를 mock 해 <b>오케스트레이션과 금지 동작</b>을 검증한다.
 */
class JobPostingReextractionServiceTest {

    @Test
    void rejectsBlankProviderBeforeAnySideEffect() {
        Fixture f = new Fixture();

        assertThatThrownBy(() -> f.service.reextract(1L, 10L, "  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        // provider 는 소유권 확인·추출 행 생성보다 먼저 검증 → 아무 부수효과도 없어야 한다.
        verify(f.accessService, never()).requireOwned(anyLong(), anyLong());
        verify(f.extractionMapper, never()).findLatestExtractionByApplicationCaseId(anyLong());
        verify(f.extractionMapper, never()).insertApplicationCaseExtraction(any());
    }

    @Test
    void rejectsUnknownProvider() {
        Fixture f = new Fixture();

        assertThatThrownBy(() -> f.service.reextract(1L, 10L, "gpt-9"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(f.extractionMapper, never()).insertApplicationCaseExtraction(any());
    }

    @Test
    void rejectsWhenLatestExtractionIsStillActive() {
        // 진행 중(QUEUED/RUNNING)인 추출은 재추출 대상이 아니다 — 종결(성공/실패)된 최신만 허용.
        Fixture f = new Fixture();
        when(f.extractionMapper.findLatestExtractionByApplicationCaseId(10L))
                .thenReturn(extraction(40L, 10L, 20L, "PDF", "RUNNING"));

        assertThatThrownBy(() -> f.service.reextract(1L, 10L, "CLAUDE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(f.extractionMapper, never()).insertApplicationCaseExtraction(any());
    }

    @Test
    void allowsReextractionWhenLatestSucceeded() {
        // 성공한 공고도 다른 OCR 모델로 재추출할 수 있다(품질 개선). SUCCEEDED 최신은 거절되지 않고 새 strict 추출을
        // 만들며, 성공 시 자동 분석 없이 finalizeSucceeded 까지만 간다(기존 분석은 revision 비교로 stale).
        Fixture f = new Fixture();
        f.stubTerminalPdfLatest("SUCCEEDED");
        JobPosting posting = JobPosting.builder()
                .id(20L).applicationCaseId(10L).revision(1)
                .uploadedFileUrl("local:application-postings/10/posting.pdf").sourceType("PDF").build();
        when(f.jobPostingService.getJobPostingDomainForCase(1L, 10L, 20L)).thenReturn(posting);
        ExtractedPosting extracted = new ExtractedPosting("PDF", posting.getUploadedFileUrl(), null, "재추출 본문", null,
                "claude", "claude-x");
        when(f.jobPostingService.extractUploadedJobPostingStrict(1L, 10L, "PDF", posting.getUploadedFileUrl(), "CLAUDE"))
                .thenReturn(extracted);
        ExtractionResult result = new ExtractionResult(posting, extracted, null, true, null, false);
        when(f.processor.evaluate(any(ApplicationCaseExtraction.class), eq(posting), eq(extracted))).thenReturn(result);

        f.service.reextract(1L, 10L, "CLAUDE");

        verify(f.extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
        verify(f.jobPostingService).extractUploadedJobPostingStrict(1L, 10L, "PDF", posting.getUploadedFileUrl(), "CLAUDE");
        verify(f.processor).finalizeSucceeded(any(ApplicationCaseExtraction.class), eq(result));
        verify(f.processor, never()).finalizeFailed(any(), any(), any());
    }

    @Test
    void rejectsNonOcrSourceType() {
        Fixture f = new Fixture();
        // OCR 모델 선택 재추출은 PDF/IMAGE 에만 적용 — URL 실패 재추출은 거절한다.
        when(f.extractionMapper.findLatestExtractionByApplicationCaseId(10L))
                .thenReturn(extraction(40L, 10L, 20L, "URL", "FAILED"));

        assertThatThrownBy(() -> f.service.reextract(1L, 10L, "CLAUDE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(f.extractionMapper, never()).insertApplicationCaseExtraction(any());
    }

    @Test
    void rejectsWhenActiveExtractionExists() {
        Fixture f = new Fixture();
        when(f.extractionMapper.findLatestExtractionByApplicationCaseId(10L))
                .thenReturn(extraction(40L, 10L, 20L, "PDF", "FAILED"));
        when(f.extractionMapper.countActiveExtractionsByApplicationCaseId(10L)).thenReturn(1);

        assertThatThrownBy(() -> f.service.reextract(1L, 10L, "CLAUDE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(f.extractionMapper, never()).insertApplicationCaseExtraction(any());
    }

    @Test
    void successInsertsClaimsRunsStrictOcrAndFinalizesWithoutAutoAnalysis() {
        Fixture f = new Fixture();
        f.stubFailedPdfLatest();
        JobPosting posting = JobPosting.builder()
                .id(20L).applicationCaseId(10L).revision(1)
                .uploadedFileUrl("local:application-postings/10/posting.pdf").sourceType("PDF").build();
        when(f.jobPostingService.getJobPostingDomainForCase(1L, 10L, 20L)).thenReturn(posting);
        ExtractedPosting extracted = new ExtractedPosting("PDF", posting.getUploadedFileUrl(), null, "재추출 본문", null,
                "claude", "claude-x");
        when(f.jobPostingService.extractUploadedJobPostingStrict(1L, 10L, "PDF", posting.getUploadedFileUrl(), "CLAUDE"))
                .thenReturn(extracted);
        ExtractionResult result = new ExtractionResult(posting, extracted, null, true, null, false);
        when(f.processor.evaluate(any(ApplicationCaseExtraction.class), eq(posting), eq(extracted))).thenReturn(result);

        f.service.reextract(1L, 10L, "CLAUDE");

        // 새 추출 행은 QUEUED 로 삽입(선택 provider 스냅샷) 후 같은 짧은 TX 에서 RUNNING 으로 claim.
        ArgumentCaptor<ApplicationCaseExtraction> inserted = ArgumentCaptor.forClass(ApplicationCaseExtraction.class);
        verify(f.extractionMapper).insertApplicationCaseExtraction(inserted.capture());
        assertThat(inserted.getValue().getSourceType()).isEqualTo("PDF");
        assertThat(inserted.getValue().getOcrRequestedProvider()).isEqualTo("CLAUDE");
        assertThat(inserted.getValue().getStatus()).isEqualTo("QUEUED");
        verify(f.extractionMapper).claimQueuedExtraction(41L);
        // strict = 선택 provider 하나만.
        verify(f.jobPostingService).extractUploadedJobPostingStrict(1L, 10L, "PDF", posting.getUploadedFileUrl(), "CLAUDE");
        // 성공은 finalizeSucceeded 까지만 — 자동 분석 파이프라인은 (handoff 를 버려) 실행하지 않는다.
        verify(f.processor).finalizeSucceeded(any(ApplicationCaseExtraction.class), eq(result));
        verify(f.processor, never()).finalizeFailed(any(), any(), any());
    }

    @Test
    void failurePreservesExistingAndDoesNotTouchInitialRunProfile() {
        Fixture f = new Fixture();
        f.stubFailedPdfLatest();
        JobPosting posting = JobPosting.builder()
                .id(20L).applicationCaseId(10L).revision(1)
                .uploadedFileUrl("local:application-postings/10/posting.pdf").sourceType("PDF").build();
        when(f.jobPostingService.getJobPostingDomainForCase(1L, 10L, 20L)).thenReturn(posting);
        RuntimeException boom = new BusinessException(ErrorCode.INVALID_INPUT, "load 실패");
        when(f.jobPostingService.extractUploadedJobPostingStrict(any(), any(), any(), any(), any())).thenThrow(boom);

        f.service.reextract(1L, 10L, "CLAUDE");

        // 실패는 finalizeFailed 로 종결하되 초기 실행 프로필을 건드리지 않는다(PostFailureAction.NONE).
        ArgumentCaptor<PostFailureAction> action = ArgumentCaptor.forClass(PostFailureAction.class);
        verify(f.processor).finalizeFailed(any(ApplicationCaseExtraction.class), eq(boom), action.capture());
        assertThat(action.getValue()).isSameAs(PostFailureAction.NONE);
        verify(f.processor, never()).finalizeSucceeded(any(), any());
    }

    @Test
    void conflictsWhenClaimLosesRaceAndSkipsOcr() {
        Fixture f = new Fixture();
        f.stubFailedPdfLatest();
        when(f.jobPostingService.getJobPostingDomainForCase(1L, 10L, 20L)).thenReturn(JobPosting.builder()
                .id(20L).applicationCaseId(10L).uploadedFileUrl("local:x.pdf").sourceType("PDF").build());
        when(f.extractionMapper.claimQueuedExtraction(41L)).thenReturn(0); // 경합 패배

        assertThatThrownBy(() -> f.service.reextract(1L, 10L, "CLAUDE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(f.jobPostingService, never()).extractUploadedJobPostingStrict(any(), any(), any(), any(), any());
        verify(f.processor, never()).finalizeSucceeded(any(), any());
        verify(f.processor, never()).finalizeFailed(any(), any(), any());
    }

    @Test
    void conflictsWhenInsertHitsActiveUniqueConstraint() {
        Fixture f = new Fixture();
        f.stubFailedPdfLatest();
        when(f.jobPostingService.getJobPostingDomainForCase(1L, 10L, 20L)).thenReturn(JobPosting.builder()
                .id(20L).applicationCaseId(10L).uploadedFileUrl("local:x.pdf").sourceType("PDF").build());
        doThrow(new DuplicateKeyException("uk_case_extraction_active"))
                .when(f.extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));

        assertThatThrownBy(() -> f.service.reextract(1L, 10L, "CLAUDE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(f.jobPostingService, never()).extractUploadedJobPostingStrict(any(), any(), any(), any(), any());
    }

    // --- #1 초기 실행 프로필 상태전이 가드: 재추출이 초기 자동 파이프라인과 경합하지 않고, PENDING 프로필을
    //     닫아 재추출 후 수동 분석이 영구 409 로 막히지 않게 한다. ---

    @Test
    void rejectsWhenCaseIsAnalyzingWithoutSideEffect() {
        // 초기 파이프라인 실행 중(케이스 ANALYZING)에는 extraction 이 이미 SUCCEEDED 라 countActive=0 이어도
        // 재추출을 거절한다 — 분석이 읽는 공고 revision 과의 경합을 막는다. 부수효과 없음.
        Fixture f = new Fixture();
        f.stubFailedPdfLatest();
        when(f.accessService.requireOwned(1L, 10L)).thenReturn(ownedCase("ANALYZING"));

        assertThatThrownBy(() -> f.service.reextract(1L, 10L, "CLAUDE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(f.extractionMapper, never()).insertApplicationCaseExtraction(any());
        verify(f.jobPostingService, never()).extractUploadedJobPostingStrict(any(), any(), any(), any(), any());
        verify(f.initialRunMapper, never()).claimForRun(anyLong(), any());
        verify(f.initialRunMapper, never()).markFailed(anyLong(), any(), any());
    }

    @Test
    void rejectsWhenInitialRunProfileIsRunningWithoutSideEffect() {
        // 프로필이 RUNNING = 초기 파이프라인이 이미 claim 하고 실행 중 → 재추출 거절. 부수효과 없음.
        Fixture f = new Fixture();
        f.stubFailedPdfLatest();
        when(f.initialRunMapper.findByApplicationCaseId(10L)).thenReturn(initialRun("RUNNING"));

        assertThatThrownBy(() -> f.service.reextract(1L, 10L, "CLAUDE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(f.extractionMapper, never()).insertApplicationCaseExtraction(any());
        verify(f.jobPostingService, never()).extractUploadedJobPostingStrict(any(), any(), any(), any(), any());
        verify(f.initialRunMapper, never()).claimForRun(anyLong(), any());
        verify(f.initialRunMapper, never()).markFailed(anyLong(), any(), any());
    }

    @Test
    void closesPendingInitialRunProfileThenReextractsOnPass() {
        // 최초 OCR 이 REVIEW_REQUIRED 라 초기 실행 프로필이 PENDING 으로 남아 있고, 사용자가 confirm 대신
        // 다른 OCR 모델로 재추출한다. 프로필을 원자적으로 claim 후 FAILED 로 닫고 재추출을 진행한다
        // → 이후 수동 job/company 분석이 열린다(프로필이 PENDING/RUNNING 이 아니므로 가드 통과).
        Fixture f = new Fixture();
        f.stubFailedPdfLatest();
        when(f.initialRunMapper.findByApplicationCaseId(10L)).thenReturn(initialRun("PENDING"));
        when(f.initialRunMapper.claimForRun(eq(10L), any())).thenReturn(1);
        JobPosting posting = JobPosting.builder()
                .id(20L).applicationCaseId(10L).revision(1)
                .uploadedFileUrl("local:x.pdf").sourceType("PDF").build();
        when(f.jobPostingService.getJobPostingDomainForCase(1L, 10L, 20L)).thenReturn(posting);
        ExtractedPosting extracted = new ExtractedPosting("PDF", posting.getUploadedFileUrl(), null, "재추출 본문", null,
                "claude", "claude-x");
        when(f.jobPostingService.extractUploadedJobPostingStrict(1L, 10L, "PDF", posting.getUploadedFileUrl(), "CLAUDE"))
                .thenReturn(extracted);
        ExtractionResult result = new ExtractionResult(posting, extracted, null, true, null, false);
        when(f.processor.evaluate(any(ApplicationCaseExtraction.class), eq(posting), eq(extracted))).thenReturn(result);

        f.service.reextract(1L, 10L, "CLAUDE");

        // 프로필을 claim 한 토큰 그대로 FAILED 로 닫는다(재추출은 초기 자동 분석을 되살리지 않는다).
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(f.initialRunMapper).claimForRun(eq(10L), token.capture());
        verify(f.initialRunMapper).markFailed(eq(10L), eq(token.getValue()), any());
        // 재추출은 정상 진행: 새 추출 행 생성 + strict OCR + finalizeSucceeded(자동 분석 없음).
        verify(f.extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
        verify(f.jobPostingService).extractUploadedJobPostingStrict(1L, 10L, "PDF", posting.getUploadedFileUrl(), "CLAUDE");
        verify(f.processor).finalizeSucceeded(any(ApplicationCaseExtraction.class), eq(result));
    }

    @Test
    void closesPendingInitialRunProfileThenReextractsOnFailure() {
        // PENDING 프로필을 닫은 뒤 재추출이 실패해도 프로필은 FAILED 로 종결된 상태 → 이후 재추출 재시도와
        // 수동 분석이 모두 가능하다. 실패 자체는 기존 공고 보존(finalizeFailed NONE).
        Fixture f = new Fixture();
        f.stubFailedPdfLatest();
        when(f.initialRunMapper.findByApplicationCaseId(10L)).thenReturn(initialRun("PENDING"));
        when(f.initialRunMapper.claimForRun(eq(10L), any())).thenReturn(1);
        JobPosting posting = JobPosting.builder()
                .id(20L).applicationCaseId(10L).revision(1)
                .uploadedFileUrl("local:x.pdf").sourceType("PDF").build();
        when(f.jobPostingService.getJobPostingDomainForCase(1L, 10L, 20L)).thenReturn(posting);
        RuntimeException boom = new BusinessException(ErrorCode.INVALID_INPUT, "load 실패");
        when(f.jobPostingService.extractUploadedJobPostingStrict(any(), any(), any(), any(), any())).thenThrow(boom);

        f.service.reextract(1L, 10L, "CLAUDE");

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(f.initialRunMapper).claimForRun(eq(10L), token.capture());
        verify(f.initialRunMapper).markFailed(eq(10L), eq(token.getValue()), any());
        ArgumentCaptor<PostFailureAction> action = ArgumentCaptor.forClass(PostFailureAction.class);
        verify(f.processor).finalizeFailed(any(ApplicationCaseExtraction.class), eq(boom), action.capture());
        assertThat(action.getValue()).isSameAs(PostFailureAction.NONE);
    }

    @Test
    void rejectsWhenPendingProfileClaimLosesRaceWithoutSideEffect() {
        // PENDING 을 읽었지만 그 사이 초기 파이프라인이 선점(claimForRun 0행) → 경합이므로 거절하고, 재추출은
        // 시작하지 않는다(OCR·추출 행 없음). markFailed 도 없다(claim 에 실패했으므로).
        Fixture f = new Fixture();
        f.stubFailedPdfLatest();
        when(f.initialRunMapper.findByApplicationCaseId(10L)).thenReturn(initialRun("PENDING"));
        when(f.initialRunMapper.claimForRun(eq(10L), any())).thenReturn(0);
        when(f.jobPostingService.getJobPostingDomainForCase(1L, 10L, 20L)).thenReturn(JobPosting.builder()
                .id(20L).applicationCaseId(10L).uploadedFileUrl("local:x.pdf").sourceType("PDF").build());

        assertThatThrownBy(() -> f.service.reextract(1L, 10L, "CLAUDE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(f.extractionMapper, never()).insertApplicationCaseExtraction(any());
        verify(f.jobPostingService, never()).extractUploadedJobPostingStrict(any(), any(), any(), any(), any());
        verify(f.initialRunMapper, never()).markFailed(anyLong(), any(), any());
    }

    // --- 실제 processor 결합 회귀 테스트: strict lifecycle(저장·상태 전이·REVIEW_REQUIRED·알림·보존)을
    //     mock 이 아닌 real JobPostingExtractionProcessor 로 통과시켜 strict-2 필수 조건을 잠근다. ---

    @Test
    void strictPassSavesNewRevisionMarksSucceededAndDoesNotAutoAnalyze() {
        RealFixture f = new RealFixture();
        // 텍스트 PDF 도 함께 대표: PDFBox 우선이면 ocrProvider="pdfbox" 로 온다.
        ExtractedPosting extracted = extractedPosting("PASS", "재추출된 공고 본문입니다. 상세 요건과 자격 조건이 충분히 담겨 있습니다.", "pdfbox");
        f.stubStrictOcr(extracted);
        when(f.extractionMapper.findRunningExtractionForUpdate(41L))
                .thenReturn(extraction(41L, 10L, 20L, "PDF", "RUNNING"));
        when(f.jobPostingService.saveExtractedJobPosting(eq(1L), eq(10L), any(ExtractedPosting.class)))
                .thenReturn(new JobPostingResponse(50L, 10L, 2, null, "local:x.pdf", extracted.extractedText(), "PDF", null));
        when(f.extractionMapper.markExtractionSucceeded(eq(41L), any(), any(), any(), eq("PASS"), any(), any(), anyBoolean(), any()))
                .thenReturn(1);
        when(f.applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L))
                .thenReturn(com.careertuner.applicationcase.domain.ApplicationCase.builder().id(10L).userId(1L).build());

        f.service.reextract(1L, 10L, "CLAUDE");

        // 새 revision 저장 + SUCCEEDED(PASS) 마킹 + 케이스 메타 갱신 + 성공 알림. 실패 마킹은 없다.
        verify(f.jobPostingService).saveExtractedJobPosting(eq(1L), eq(10L), any(ExtractedPosting.class));
        verify(f.extractionMapper).markExtractionSucceeded(eq(41L), eq(50L), any(), any(), eq("PASS"), any(), any(), anyBoolean(), any());
        verify(f.applicationCaseMapper).updateApplicationCase(any());
        verify(f.extractionMapper, never()).markExtractionFailed(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        assertThat(f.notifiedTypes()).contains("JOB_POSTING_EXTRACTION_SUCCEEDED");
    }

    @Test
    void strictReviewRequiredNotifiesReviewWithoutMetadataOrAutoAnalysis() {
        RealFixture f = new RealFixture();
        f.stubStrictOcr(extractedPosting("REVIEW_REQUIRED", "짧고 애매한 재추출 본문", "claude"));
        when(f.extractionMapper.findRunningExtractionForUpdate(41L))
                .thenReturn(extraction(41L, 10L, 20L, "PDF", "RUNNING"));
        when(f.jobPostingService.saveExtractedJobPosting(eq(1L), eq(10L), any(ExtractedPosting.class)))
                .thenReturn(new JobPostingResponse(50L, 10L, 2, null, "local:x.pdf", "짧고 애매한 재추출 본문", "PDF", null));
        when(f.extractionMapper.markExtractionSucceeded(eq(41L), any(), any(), any(), eq("REVIEW_REQUIRED"), any(), any(), anyBoolean(), any()))
                .thenReturn(1);

        f.service.reextract(1L, 10L, "CLAUDE");

        // REVIEW_REQUIRED 는 성공 이력이되 검수 알림으로 끝난다 — 케이스 메타 갱신·성공 알림·자동 분석 없음.
        verify(f.extractionMapper).markExtractionSucceeded(eq(41L), any(), any(), any(), eq("REVIEW_REQUIRED"), any(), any(), anyBoolean(), any());
        verify(f.applicationCaseMapper, never()).updateApplicationCase(any());
        assertThat(f.notifiedTypes()).contains("JOB_POSTING_EXTRACTION_REVIEW_REQUIRED");
        assertThat(f.notifiedTypes()).doesNotContain("JOB_POSTING_EXTRACTION_SUCCEEDED");
    }

    @Test
    void strictProviderFailureMarksFailedAndPreservesExistingPosting() {
        RealFixture f = new RealFixture();
        // strict OCR 실패는 예외가 아니라 FAILED 마커로 온다(교차 폴백 없음).
        f.stubStrictOcr(new ExtractedPosting("PDF", "local:x.pdf", null, "", null,
                "IMAGE_PDF_OCR", 0, "FAILED", null, null, false,
                "선택한 OCR 모델(CLAUDE)로 공고문을 추출하지 못했습니다.", null, null));
        when(f.extractionMapper.markExtractionFailed(eq(41L), any(), any(), any(), eq("FAILED"), any(), any(), anyBoolean(), any()))
                .thenReturn(1);

        f.service.reextract(1L, 10L, "CLAUDE");

        // 실패는 FAILED 이력만 — 기존 공고문을 새 revision 으로 덮지 않고(save 없음), 성공 마킹도 없다.
        verify(f.extractionMapper).markExtractionFailed(eq(41L), any(), any(), any(), eq("FAILED"), any(), any(), anyBoolean(), any());
        verify(f.jobPostingService, never()).saveExtractedJobPosting(any(), any(), any());
        verify(f.extractionMapper, never()).markExtractionSucceeded(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(f.applicationCaseMapper, never()).updateApplicationCase(any());
        assertThat(f.notifiedTypes()).contains("JOB_POSTING_EXTRACTION_FAILED");
    }

    @Test
    void strictFailureOnSucceededLatestPreservesExistingSuccessRevision() {
        // #3 핵심 시나리오: 최신 추출이 SUCCEEDED(성공 공고)인데 사용자가 다른 OCR 모델로 재추출했다가 실패해도,
        // 기존 성공 revision·분석을 보존한다 — 새 FAILED 이력만 남고 save(새 revision)·성공 마킹·케이스 메타 갱신은 없다.
        RealFixture f = new RealFixture();
        // 기본 fixture 의 latest=FAILED 를 SUCCEEDED 로 덮어 성공 공고 재추출 시나리오로 만든다.
        when(f.extractionMapper.findLatestExtractionByApplicationCaseId(10L))
                .thenReturn(extraction(40L, 10L, 20L, "PDF", "SUCCEEDED"));
        // strict OCR 실패는 예외가 아니라 FAILED 마커로 온다(교차 폴백 없음).
        f.stubStrictOcr(new ExtractedPosting("PDF", "local:x.pdf", null, "", null,
                "IMAGE_PDF_OCR", 0, "FAILED", null, null, false,
                "선택한 OCR 모델(CLAUDE)로 공고문을 추출하지 못했습니다.", null, null));
        when(f.extractionMapper.markExtractionFailed(eq(41L), any(), any(), any(), eq("FAILED"), any(), any(), anyBoolean(), any()))
                .thenReturn(1);

        f.service.reextract(1L, 10L, "CLAUDE");

        // 기존 성공 공고 보존: 새 revision save 없음 + 성공 마킹 없음 + 케이스 메타 갱신 없음. FAILED 이력만.
        verify(f.extractionMapper).markExtractionFailed(eq(41L), any(), any(), any(), eq("FAILED"), any(), any(), anyBoolean(), any());
        verify(f.jobPostingService, never()).saveExtractedJobPosting(any(), any(), any());
        verify(f.extractionMapper, never()).markExtractionSucceeded(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(f.applicationCaseMapper, never()).updateApplicationCase(any());
        assertThat(f.notifiedTypes()).contains("JOB_POSTING_EXTRACTION_FAILED");
    }

    /** strict lifecycle 을 real processor 로 구동하기 위한 fixture(저장·상태 전이·알림을 실제로 통과시킨다). */
    private static final class RealFixture {
        final ApplicationCaseAccessService accessService = mock(ApplicationCaseAccessService.class);
        final ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        final ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        final com.careertuner.applicationcase.mapper.ApplicationCaseMapper applicationCaseMapper =
                mock(com.careertuner.applicationcase.mapper.ApplicationCaseMapper.class);
        final JobPostingService jobPostingService = mock(JobPostingService.class);
        final NotificationService notificationService = mock(NotificationService.class);
        final JobPostingExtractionProcessor processor = new JobPostingExtractionProcessor(
                extractionMapper,
                applicationCaseMapper,
                jobPostingService,
                new ApplicationCaseExtractionQualityGate(new ObjectMapper()),
                mock(OpenAiResponsesClient.class),
                mock(AiUsageLogService.class),
                notificationService,
                synchronousTransactionTemplate());
        final JobPostingReextractionService service = new JobPostingReextractionService(
                accessService, extractionMapper, initialRunMapper, jobPostingService, processor, synchronousTransactionTemplate());

        RealFixture() {
            when(accessService.requireOwned(1L, 10L)).thenReturn(ownedCase("READY"));
            when(extractionMapper.findLatestExtractionByApplicationCaseId(10L))
                    .thenReturn(extraction(40L, 10L, 20L, "PDF", "FAILED"));
            when(extractionMapper.countActiveExtractionsByApplicationCaseId(10L)).thenReturn(0);
            doAnswer(invocation -> {
                invocation.<ApplicationCaseExtraction>getArgument(0).setId(41L);
                return null;
            }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
            when(extractionMapper.claimQueuedExtraction(41L)).thenReturn(1);
            when(extractionMapper.findExtractionById(41L)).thenReturn(extraction(41L, 10L, 20L, "PDF", "SUCCEEDED"));
            when(jobPostingService.getJobPostingDomainForCase(1L, 10L, 20L)).thenReturn(JobPosting.builder()
                    .id(20L).applicationCaseId(10L).revision(1)
                    .uploadedFileUrl("local:x.pdf").sourceType("PDF").build());
        }

        void stubStrictOcr(ExtractedPosting extracted) {
            when(jobPostingService.extractUploadedJobPostingStrict(eq(1L), eq(10L), eq("PDF"), any(), eq("CLAUDE")))
                    .thenReturn(extracted);
        }

        java.util.List<String> notifiedTypes() {
            ArgumentCaptor<com.careertuner.notification.domain.Notification> captor =
                    ArgumentCaptor.forClass(com.careertuner.notification.domain.Notification.class);
            verify(notificationService, org.mockito.Mockito.atLeast(0)).notify(captor.capture());
            return captor.getAllValues().stream()
                    .map(com.careertuner.notification.domain.Notification::getType)
                    .toList();
        }
    }

    private static ExtractedPosting extractedPosting(String qualityStatus, String text, String ocrProvider) {
        return new ExtractedPosting("PDF", "local:x.pdf", null, text, null,
                "IMAGE_PDF_OCR", 90, qualityStatus, "{}", "{}", false, null, ocrProvider, ocrProvider + "-model");
    }

    private static final class Fixture {
        final ApplicationCaseAccessService accessService = mock(ApplicationCaseAccessService.class);
        final ApplicationCaseExtractionMapper extractionMapper = mock(ApplicationCaseExtractionMapper.class);
        final ApplicationCaseInitialRunMapper initialRunMapper = mock(ApplicationCaseInitialRunMapper.class);
        final JobPostingService jobPostingService = mock(JobPostingService.class);
        final JobPostingExtractionProcessor processor = mock(JobPostingExtractionProcessor.class);
        final JobPostingReextractionService service = new JobPostingReextractionService(
                accessService, extractionMapper, initialRunMapper, jobPostingService, processor, synchronousTransactionTemplate());

        Fixture() {
            // 기본: 소유 케이스는 분석 진행 중이 아니고(READY), 초기 실행 프로필은 없다(가드 통과).
            when(accessService.requireOwned(1L, 10L)).thenReturn(ownedCase("READY"));
        }

        /** 실패한 PDF 최신 추출 + 진행 중 없음 + insert 시 id=41 부여 + claim 성공을 기본 세팅한다. */
        void stubFailedPdfLatest() {
            stubTerminalPdfLatest("FAILED");
        }

        /** 종결(성공/실패) 상태의 PDF 최신 추출 + 진행 중 없음 + insert id=41 + claim 성공을 세팅한다. */
        void stubTerminalPdfLatest(String latestStatus) {
            when(extractionMapper.findLatestExtractionByApplicationCaseId(10L))
                    .thenReturn(extraction(40L, 10L, 20L, "PDF", latestStatus));
            when(extractionMapper.countActiveExtractionsByApplicationCaseId(10L)).thenReturn(0);
            doAnswer(invocation -> {
                invocation.<ApplicationCaseExtraction>getArgument(0).setId(41L);
                return null;
            }).when(extractionMapper).insertApplicationCaseExtraction(any(ApplicationCaseExtraction.class));
            when(extractionMapper.claimQueuedExtraction(41L)).thenReturn(1);
            // 응답은 이번 재추출이 만든 행(id 41)을 id 로 다시 읽어 만든다.
            when(extractionMapper.findExtractionById(41L)).thenReturn(extraction(41L, 10L, 20L, "PDF", "SUCCEEDED"));
        }
    }

    private static ApplicationCase ownedCase(String status) {
        return ApplicationCase.builder().id(10L).userId(1L).status(status).build();
    }

    private static ApplicationCaseInitialRun initialRun(String state) {
        return ApplicationCaseInitialRun.builder().applicationCaseId(10L).state(state).build();
    }

    private static ApplicationCaseExtraction extraction(Long id, Long caseId, Long jobPostingId,
                                                        String sourceType, String status) {
        return ApplicationCaseExtraction.builder()
                .id(id).applicationCaseId(caseId).jobPostingId(jobPostingId).userId(1L)
                .sourceType(sourceType).status(status).build();
    }

    private static TransactionTemplate synchronousTransactionTemplate() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
    }
}
