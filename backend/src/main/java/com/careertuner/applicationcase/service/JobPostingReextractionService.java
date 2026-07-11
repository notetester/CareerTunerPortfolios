package com.careertuner.applicationcase.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;
import com.careertuner.applicationcase.dto.ApplicationCaseExtractionResponse;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.service.JobPostingExtractionProcessor.ExtractionResult;
import com.careertuner.applicationcase.service.JobPostingExtractionProcessor.PostFailureAction;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 고른 OCR 모델로 <b>종결된(성공·실패)</b> 공고 추출을 <b>동기 strict 재추출</b>한다(수동 재추출 정책).
 *
 * <p>실패 복구뿐 아니라 <b>성공한 공고도 다른 OCR 모델로 다시 뽑을 수 있다</b>(품질 개선 목적). 진행 중
 * (QUEUED/RUNNING)인 추출만 재추출 대상에서 제외한다. 배경 워커와 달리 사용자가 결과를 기다리는 동기
 * 경로이고, 다음 정책을 강제한다:
 * <ul>
 *   <li>provider 필수 · 단일 provider 만 호출(교차 provider 폴백 금지) → {@code extractFileStrict}.</li>
 *   <li>OCR 은 PDF·이미지에만 적용(모델 선택 = OCR 모델). 그 외 sourceType 재추출은 거절.</li>
 *   <li>성공 시 새 revision 저장까지만 — <b>자동 분석을 실행하지 않는다</b>(finalizeSucceeded 의 handoff 를 버림).
 *       기존 분석은 revision 비교로 stale 처리되고, 사용자가 모델을 골라 수동 재분석한다.</li>
 *   <li>실패 시 기존 공고·분석을 보존하고 <b>초기 실행 프로필을 건드리지 않는다</b>(reopen/abandon 없음).
 *       성공한 공고를 재추출하다 실패해도 새 FAILED 이력만 남고 기존 성공 revision·분석은 그대로다.</li>
 *   <li>트랜잭션 경계: (insert QUEUED + claim RUNNING) 은 <b>같은 짧은 TX</b>(중간 커밋 시 배경 워커가
 *       QUEUED 행을 선점해 자동 체인으로 처리할 수 있음) → OCR 은 트랜잭션 밖 → 성공/실패 마킹은 다시 짧은 TX.</li>
 * </ul>
 * 저장/상태 전이/REVIEW_REQUIRED/알림 lifecycle 은 {@link JobPostingExtractionProcessor} 를 워커와 공유한다.
 */
@Service
@RequiredArgsConstructor
public class JobPostingReextractionService {

    private static final String EXTRACTION_STATUS_QUEUED = "QUEUED";
    private static final String EXTRACTION_STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String EXTRACTION_STATUS_FAILED = "FAILED";
    // 종결된 추출만 재추출 대상 — 성공한 공고도 다른 OCR 모델로 다시 뽑을 수 있고, 진행 중(QUEUED/RUNNING)은 제외.
    private static final Set<String> REEXTRACTABLE_STATUSES = Set.of(EXTRACTION_STATUS_SUCCEEDED, EXTRACTION_STATUS_FAILED);
    private static final Set<String> OCR_PROVIDERS = Set.of("CLAUDE", "OPENAI", "SELF_OCR");
    private static final Set<String> OCR_SOURCE_TYPES = Set.of("PDF", "IMAGE");

    private final ApplicationCaseAccessService accessService;
    private final ApplicationCaseExtractionMapper extractionMapper;
    private final JobPostingService jobPostingService;
    private final JobPostingExtractionProcessor extractionProcessor;
    private final TransactionTemplate transactionTemplate;

    public ApplicationCaseExtractionResponse reextract(Long userId, Long applicationCaseId, String ocrProvider) {
        // 부수효과(새 추출 행) 전에 provider 를 먼저 검증 — 잘못된 값은 아무 행도 만들지 않고 400.
        String provider = validateOcrProvider(ocrProvider);
        accessService.requireOwned(userId, applicationCaseId);

        ApplicationCaseExtraction latest = extractionMapper.findLatestExtractionByApplicationCaseId(applicationCaseId);
        if (latest == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Job posting extraction job was not found.");
        }
        // 종결된(성공/실패) 최신 추출만 재추출 대상 — QUEUED/RUNNING(진행 중)은 제외한다(아래 countActive 로도 방어).
        if (!REEXTRACTABLE_STATUSES.contains(latest.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 진행 중인 공고 추출 작업이 있습니다.");
        }
        String sourceType = normalizeSourceType(latest.getSourceType());
        if (!OCR_SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "OCR 재추출은 PDF·이미지 공고에만 지원됩니다.");
        }
        Long latestJobPostingId = latest.getJobPostingId();
        if (latestJobPostingId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고문을 찾을 수 없습니다.");
        }
        if (extractionMapper.countActiveExtractionsByApplicationCaseId(applicationCaseId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 진행 중인 공고 추출 작업이 있습니다.");
        }
        JobPosting posting = jobPostingService.getJobPostingDomainForCase(userId, applicationCaseId, latestJobPostingId);

        ApplicationCaseExtraction extraction = insertAndClaim(userId, applicationCaseId, latestJobPostingId, sourceType, provider);

        try {
            // OCR 원격 호출은 트랜잭션 밖. strict = 선택 provider 하나만, 교차 폴백 없음.
            ExtractedPosting extracted = jobPostingService.extractUploadedJobPostingStrict(
                    userId, applicationCaseId, sourceType, posting.getUploadedFileUrl(), provider);
            ExtractionResult result = extractionProcessor.evaluate(extraction, posting, extracted);
            // handoff 를 버려 자동 분석을 실행하지 않는다(수동 재분석 정책).
            extractionProcessor.finalizeSucceeded(extraction, result);
        } catch (RuntimeException ex) {
            // 실패해도 기존 공고·분석 보존. 초기 실행 프로필은 건드리지 않는다(NONE).
            extractionProcessor.finalizeFailed(extraction, ex, PostFailureAction.NONE);
        }

        // 이번 strict 재추출이 만든 행 자체를 id 로 다시 읽는다 — 그 사이 다른 추출이 생겨도 latest 로 엉뚱한 행을
        // 반환하지 않게 한다(방금 종결한 행의 최종 상태를 정확히 응답).
        return ApplicationCaseExtractionResponse.from(
                extractionMapper.findExtractionById(extraction.getId()));
    }

    /** insert QUEUED 와 claim RUNNING 을 같은 짧은 TX 로 묶어, 커밋 시 이미 RUNNING(=워커 선점 불가)이 되게 한다. */
    private ApplicationCaseExtraction insertAndClaim(Long userId,
                                                     Long applicationCaseId,
                                                     Long jobPostingId,
                                                     String sourceType,
                                                     String provider) {
        ApplicationCaseExtraction extraction = ApplicationCaseExtraction.builder()
                .applicationCaseId(applicationCaseId)
                .jobPostingId(jobPostingId)
                .userId(userId)
                .sourceType(sourceType)
                .ocrRequestedProvider(provider)
                .status(EXTRACTION_STATUS_QUEUED)
                .build();
        return transactionTemplate.execute(status -> {
            try {
                extractionMapper.insertApplicationCaseExtraction(extraction);
            } catch (DuplicateKeyException ex) {
                throw new BusinessException(ErrorCode.CONFLICT, "이미 진행 중인 공고 추출 작업이 있습니다.");
            }
            if (extractionMapper.claimQueuedExtraction(extraction.getId()) != 1) {
                status.setRollbackOnly();
                throw new BusinessException(ErrorCode.CONFLICT, "공고 추출 작업을 시작하지 못했습니다. 다시 시도해 주세요.");
            }
            return extraction;
        });
    }

    private static String validateOcrProvider(String ocrProvider) {
        if (ocrProvider == null || ocrProvider.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "strict 재추출에는 OCR 모델(provider) 선택이 필요합니다.");
        }
        String normalized = ocrProvider.trim().toUpperCase(Locale.ROOT);
        if (!OCR_PROVIDERS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 OCR 모델(provider)입니다.");
        }
        return normalized;
    }

    private static String normalizeSourceType(String sourceType) {
        return sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
    }
}
