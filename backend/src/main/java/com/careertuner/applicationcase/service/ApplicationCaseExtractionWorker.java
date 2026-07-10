package com.careertuner.applicationcase.service;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.service.JobPostingExtractionProcessor.ExtractionResult;
import com.careertuner.applicationcase.service.JobPostingExtractionProcessor.PipelineHandoff;
import com.careertuner.applicationcase.service.JobPostingExtractionProcessor.PostFailureAction;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.mapper.JobPostingMapper;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 큐에 쌓인 공고 추출 작업을 배경에서 폴링·처리하는 워커.
 *
 * <p>추출 방식(자동 체인)만 이 클래스가 정하고, 저장/상태 전이/알림 같은 lifecycle 은
 * {@link JobPostingExtractionProcessor} 에 위임한다(동기 strict 재추출과 공유). 워커만의 두 이음새:
 * 성공 뒤 자동 분석 파이프라인 실행, 실패 뒤 초기 실행 프로필 종료(abandon).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationCaseExtractionWorker {

    private static final int BATCH_SIZE = 5;

    private final ApplicationCaseExtractionMapper extractionMapper;
    private final JobPostingMapper jobPostingMapper;
    private final JobPostingService jobPostingService;
    private final ApplicationCaseAutoPipelineService autoPipelineService;
    private final JobPostingExtractionProcessor extractionProcessor;
    private final TransactionTemplate transactionTemplate;

    @Value("${careertuner.extraction.worker.running-timeout-minutes:30}")
    private long runningTimeoutMinutes = 30;

    @Scheduled(
            initialDelayString = "${careertuner.extraction.worker.initial-delay-ms:5000}",
            fixedDelayString = "${careertuner.extraction.worker.fixed-delay-ms:5000}")
    public void runScheduled() {
        try {
            processQueuedExtractions();
        } catch (RuntimeException ex) {
            log.warn("Application case extraction worker polling skipped: {}", rootCauseMessage(ex));
        }
    }

    public int processQueuedExtractions() {
        int processed = expireStaleRunningExtractions();
        for (ApplicationCaseExtraction extraction : extractionMapper.findQueuedExtractions(BATCH_SIZE)) {
            if (!claim(extraction.getId())) {
                continue;
            }
            processClaimed(extraction);
            processed++;
        }
        return processed;
    }

    private int expireStaleRunningExtractions() {
        List<ApplicationCaseExtraction> staleExtractions = extractionMapper.findStaleRunningExtractions(
                Math.max(1, runningTimeoutMinutes),
                BATCH_SIZE);
        if (staleExtractions == null || staleExtractions.isEmpty()) {
            return 0;
        }
        int expired = 0;
        for (ApplicationCaseExtraction extraction : staleExtractions) {
            if (extractionProcessor.finalizeStaleFailed(extraction, runningTimeoutMinutes, abandonInitialRun())) {
                expired++;
            }
        }
        return expired;
    }

    private boolean claim(Long extractionId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                extractionMapper.claimQueuedExtraction(extractionId) == 1));
    }

    private void processClaimed(ApplicationCaseExtraction extraction) {
        try {
            ExtractionResult result = extractAndAnalyze(extraction);
            // 성공 종결은 파이프라인을 실행하지 않고 handoff 만 돌려준다. 파이프라인은 커밋 뒤 트랜잭션 밖에서
            // 워커가 실행한다(case 65·66 가시성 경계 보존).
            PipelineHandoff handoff = extractionProcessor.finalizeSucceeded(extraction, result);
            if (handoff == null) {
                // 소유권 상실(stale)·마킹 경합·review-required — 파이프라인 미실행.
                return;
            }
            autoPipelineService.runAfterExtractionPass(
                    extraction.getUserId(),
                    extraction.getApplicationCaseId(),
                    handoff.jobPostingId(),
                    handoff.jobPostingRevision(),
                    handoff.postingText());
        } catch (RuntimeException ex) {
            log.warn("Application case extraction failed. extractionId={}", extraction.getId(), ex);
            extractionProcessor.finalizeFailed(extraction, ex, abandonInitialRun());
        }
    }

    /**
     * 추출 실패 종결 시 초기 실행 프로필(PENDING)을 닫아 수동 분석의 CONFLICT 영구 차단을 막는다.
     * 재추출(retry)은 초기 파이프라인 재실행을 보장하지 않는다(strict 정책: 재추출 성공은 revision 만 갱신).
     */
    private PostFailureAction abandonInitialRun() {
        return (applicationCaseId, errorMessage) -> autoPipelineService.abandonInitialRunIfPending(
                applicationCaseId,
                "공고 추출 실패로 초기 실행을 종료했습니다: " + errorMessage);
    }

    private ExtractionResult extractAndAnalyze(ApplicationCaseExtraction extraction) {
        JobPosting posting = requireJobPosting(extraction);
        ExtractedPosting extractedPosting = extractPostingText(extraction, posting);
        return extractionProcessor.evaluate(extraction, posting, extractedPosting);
    }

    private JobPosting requireJobPosting(ApplicationCaseExtraction extraction) {
        JobPosting posting = jobPostingMapper.findJobPostingByIdAndCaseId(
                extraction.getJobPostingId(),
                extraction.getApplicationCaseId());
        if (posting == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고문을 찾을 수 없습니다.");
        }
        return posting;
    }

    private ExtractedPosting extractPostingText(ApplicationCaseExtraction extraction, JobPosting posting) {
        String sourceType = normalizeSourceType(extraction.getSourceType());
        return switch (sourceType) {
            case "URL" -> jobPostingService.extractUrlJobPosting(requiredText(posting.getUploadedFileUrl(), "uploadedFileUrl"));
            case "PDF", "IMAGE" -> jobPostingService.extractUploadedJobPosting(
                    extraction.getUserId(),
                    extraction.getApplicationCaseId(),
                    sourceType,
                    requiredText(posting.getUploadedFileUrl(), "uploadedFileUrl"),
                    extraction.getOcrRequestedProvider());
            case "TEXT", "MANUAL" -> new ExtractedPosting(
                    sourceType,
                    posting.getUploadedFileUrl(),
                    posting.getOriginalText(),
                    sourceText(posting),
                    null);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "sourceType 값이 올바르지 않습니다.");
        };
    }

    private static String sourceText(JobPosting posting) {
        return requiredText(defaultText(posting.getExtractedText(), posting.getOriginalText(), null), "postingText");
    }

    private static String normalizeSourceType(String sourceType) {
        return sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
    }

    private static String requiredText(String value, String fieldName) {
        if (isBlank(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "%s 값이 필요합니다.".formatted(fieldName));
        }
        return value.trim();
    }

    private static String defaultText(String primary, String secondary, String fallback) {
        if (!isBlank(primary)) {
            return primary.trim();
        }
        if (!isBlank(secondary)) {
            return secondary.trim();
        }
        return fallback;
    }

    private static String rootCauseMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = isBlank(current.getMessage()) ? current.getClass().getSimpleName() : current.getMessage();
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
