package com.careertuner.applicationcase.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;
import com.careertuner.applicationcase.domain.ApplicationCaseInitialRun;
import com.careertuner.applicationcase.dto.ApplicationCaseExtractionResponse;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseInitialRunMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
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
 *   <li>트랜잭션 경계: (케이스 행 FOR UPDATE + 초기 실행 가드 + insert QUEUED + claim RUNNING) 은
 *       <b>같은 짧은 TX</b> — 행 잠금이 수동 분석 획득({@code markAnalyzingExclusive})과의 직렬화 지점이고,
 *       중간 커밋 시 배경 워커가 QUEUED 행을 선점할 수 있어 묶는다 → OCR 은 트랜잭션 밖 → 마킹은 다시 짧은 TX.</li>
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
    // 초기 자동 파이프라인이 실행 중임을 나타내는 케이스 상태(진입게이트가 DRAFT|READY→ANALYZING 으로 마킹).
    private static final String CASE_STATUS_ANALYZING = "ANALYZING";
    // 초기 실행 프로필 상태.
    private static final String INITIAL_RUN_PENDING = "PENDING";
    private static final String INITIAL_RUN_RUNNING = "RUNNING";
    // application_case_initial_run.failure_reason 컬럼 길이(VARCHAR 255)와 맞춘다(초과 시 markFailed 가 throw).
    private static final int FAILURE_REASON_MAX_LENGTH = 255;

    private final ApplicationCaseAccessService accessService;
    private final ApplicationCaseMapper applicationCaseMapper;
    private final ApplicationCaseExtractionMapper extractionMapper;
    private final ApplicationCaseInitialRunMapper initialRunMapper;
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

    /**
     * 재추출 획득 TX — 케이스 행 잠금 + 초기 실행 가드 + (insert QUEUED + claim RUNNING) 을 한 짧은 TX 로 묶는다.
     * 케이스 행 {@code FOR UPDATE} 가 수동 분석 획득 TX({@code markAnalyzingExclusive})와의 직렬화 지점이라
     * 스냅샷 검사 사이의 TOCTOU(분석 시작↔재추출 시작 동시 진입)가 없다. 가드가 던지면 프로필 종결까지 함께
     * 롤백되고(부수효과 없음), 커밋 시엔 이미 RUNNING 이라 배경 워커가 선점하지 못한다.
     */
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
            // 잠금 하에 최신 상태로 재검사 — 초기 파이프라인 진행 중이면 거절, PENDING 프로필은 원자적으로 닫는다.
            ApplicationCase locked = applicationCaseMapper.lockApplicationCaseById(applicationCaseId);
            if (locked == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
            }
            guardInitialRunForReextract(locked, applicationCaseId);
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

    /**
     * 초기 자동 파이프라인·수동 분석과 재추출이 같은 공고 revision 을 두고 경합하지 않도록 막는다.
     * 획득 TX 안(케이스 행 잠금 하)에서 호출된다 — {@code applicationCase} 는 방금 잠가 읽은 최신 행이라
     * 스냅샷이 아니고, 수동 분석의 ANALYZING 전이는 같은 행 잠금에 직렬화되어 이 검사와 엇갈리지 않는다.
     *
     * <ul>
     *   <li>케이스가 {@code ANALYZING}(초기 파이프라인 또는 수동 분석 진행 중)이거나 초기 실행 프로필이
     *       {@code RUNNING} 이면 CONFLICT 로 거절한다(부수효과 없음 — TX 롤백). extraction 이 이미 SUCCEEDED 라
     *       {@code countActive} 가 0인 상태에서도 분석은 돌 수 있어, 이 게이트가 그 창을 닫는다.</li>
     *   <li>프로필이 {@code PENDING}(최초 OCR 이 REVIEW_REQUIRED 라 파이프라인이 아직 실행되지 않음) 이면
     *       원자적으로 claim(PENDING→RUNNING) 한 뒤 FAILED 로 닫는다. 이렇게 하지 않으면 재추출이 PASS 여도
     *       프로필이 PENDING 으로 남아 수동 분석 가드가 영구 409 가 된다. 재추출은 정책상 초기 자동 분석을
     *       되살리지 않으므로(성공=공고 revision 만 갱신), 프로필을 FAILED 로 종결해 수동 분석 경로를 연다.</li>
     *   <li>claim 에 지면(그 사이 파이프라인이 PENDING 을 선점해 RUNNING) 경합이므로 CONFLICT 로 거절한다.</li>
     *   <li>프로필이 없거나 이미 DONE/FAILED 면 통과한다.</li>
     * </ul>
     */
    private void guardInitialRunForReextract(ApplicationCase applicationCase, Long applicationCaseId) {
        if (CASE_STATUS_ANALYZING.equals(applicationCase.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "초기 분석이 진행 중입니다. 완료된 후 다시 시도해 주세요.");
        }
        ApplicationCaseInitialRun profile = initialRunMapper.findByApplicationCaseId(applicationCaseId);
        if (profile == null) {
            return;
        }
        String state = profile.getState();
        if (INITIAL_RUN_RUNNING.equals(state)) {
            throw new BusinessException(ErrorCode.CONFLICT, "초기 분석이 진행 중입니다. 완료된 후 다시 시도해 주세요.");
        }
        if (INITIAL_RUN_PENDING.equals(state)) {
            // PENDING→FAILED 를 조건부 단일 UPDATE 로 원자적으로 닫는다 — claim(RUNNING) 후 markFailed 2단계
            // 사이에 크래시가 나면 RUNNING 고착으로 reaper 전까지 수동 분석이 막히므로, 중간 RUNNING 을 아예 거치지
            // 않는다. 0행이면 그 사이 초기 파이프라인이 PENDING 을 선점(RUNNING)/완료한 것이므로 경합으로 보고 거절.
            int closed = initialRunMapper.closePendingAsFailed(applicationCaseId,
                    truncate("사용자가 다른 OCR 모델로 재추출을 시작해 초기 자동 실행을 종료했습니다.", FAILURE_REASON_MAX_LENGTH));
            if (closed != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "초기 분석이 진행 중입니다. 완료된 후 다시 시도해 주세요.");
            }
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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
