package com.careertuner.applicationcase.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.ApplicationCaseExtractionQualityGate.QualityGateResult;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobPostingMetadataPayload;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공고 추출의 <b>상태 전이(평가 → 저장 → SUCCEEDED/FAILED 전이 → 알림)</b> 공통 로직.
 *
 * <p>배경 워커({@link ApplicationCaseExtractionWorker})와 동기 strict 재추출
 * ({@link JobPostingReextractionService})이 이 lifecycle 을 <b>복제하지 않고 공유</b>한다.
 * 두 경로의 차이는 세 이음새뿐이고, 나머지 저장/mark/REVIEW_REQUIRED/알림 순서는 여기에서만 정의한다.
 * <ol>
 *   <li>추출 방식(자동 체인 vs strict 단일 provider) — {@code evaluate()} 에 이미 만들어진
 *       {@link ExtractedPosting} 을 넘겨 받으므로 이 클래스 밖에서 결정된다.</li>
 *   <li>성공 후 자동 분석 파이프라인 실행 여부 — {@code finalizeSucceeded()} 는 절대 파이프라인을
 *       실행하지 않고 {@link PipelineHandoff} 만 반환한다. 실행 여부는 호출부가 정한다.</li>
 *   <li>실패 후 초기 실행 프로필 종료 여부 — {@code finalizeFailed()} 에 {@link PostFailureAction} 훅으로
 *       주입한다(워커=프로필 abandon, strict=아무것도 안 함).</li>
 * </ol>
 *
 * <p>짧은 트랜잭션 경계(case 65·66 가시성 튜닝)는 워커에서 옮겨온 그대로 보존한다: SUCCEEDED/FAILED 마킹과
 * 알림까지만 선커밋하고, LLM 파이프라인은 커밋 뒤 트랜잭션 밖에서 호출부가 실행한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobPostingExtractionProcessor {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final String DEFAULT_COMPANY_NAME = "기업명 확인 필요";
    private static final String DEFAULT_JOB_TITLE = "직무명 확인 필요";
    private static final String NOTIFICATION_TARGET_TYPE = "APPLICATION_CASE";
    private static final String SUCCESS_NOTIFICATION_TYPE = "JOB_POSTING_EXTRACTION_SUCCEEDED";
    private static final String FAILURE_NOTIFICATION_TYPE = "JOB_POSTING_EXTRACTION_FAILED";
    private static final String REVIEW_NOTIFICATION_TYPE = "JOB_POSTING_EXTRACTION_REVIEW_REQUIRED";
    private static final String FEATURE_JOB_POSTING_METADATA = "JOB_POSTING_METADATA";
    private static final String INVALID_WORKER_QUALITY_STATUS_REASON = "Invalid qualityStatus from Python worker.";
    private static final Pattern ENGLISH_HIRING_PATTERN = Pattern.compile(
            "(?im)^\\s*(.+?)\\s+is\\s+hiring\\s+(?:a|an)\\s+([^\\n.]+)");
    private static final Pattern DEADLINE_PATTERN = Pattern.compile(
            "(?im)(?:deadline|마감|접수)[^0-9]{0,20}(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})");

    private final ApplicationCaseExtractionMapper extractionMapper;
    private final ApplicationCaseMapper applicationCaseMapper;
    private final JobPostingService jobPostingService;
    private final ApplicationCaseExtractionQualityGate qualityGate;
    private final OpenAiResponsesClient openAiClient;
    private final AiUsageLogService aiUsageLogService;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    /**
     * DB 쓰기 없는 평가 단계 — 품질 판정과 메타(회사·직무·마감) 추출까지. OpenAI 호출이 있을 수 있으므로
     * "순수 계산"이 아니라 <b>트랜잭션 밖 외부 호출 단계</b>다(호출부가 트랜잭션 없이 부른다).
     * 품질 게이트가 FAILED 면 예외를 던져, 호출부의 catch 가 {@code finalizeFailed()} 로 라우팅한다.
     */
    public ExtractionResult evaluate(ApplicationCaseExtraction extraction,
                                     JobPosting posting,
                                     ExtractedPosting extractedPosting) {
        QualityGateResult quality = qualityFromExtractedPosting(extractedPosting);
        String postingText = defaultText(extractedPosting.extractedText(), extractedPosting.originalText(), null);
        if (quality == null) {
            postingText = requiredText(postingText, "postingText");
            quality = qualityGate.evaluate(extraction.getSourceType(), extractedPosting, postingText);
        }
        if (quality.failed()) {
            throw new ExtractionQualityFailedException(quality, "Extracted posting text did not pass the quality gate.");
        }
        postingText = requiredText(postingText, "postingText");
        boolean saveExtractedPosting = shouldSaveExtractedPosting(extraction, posting, extractedPosting);
        if (quality.reviewRequired()) {
            return new ExtractionResult(posting, extractedPosting, null, saveExtractedPosting, quality, true);
        }
        JobPostingMetadataPayload metadata = resolveJobPostingMetadata(postingText);
        return new ExtractionResult(posting, extractedPosting, metadata, saveExtractedPosting, quality, false);
    }

    /**
     * 성공 종결(짧은 트랜잭션): 소유권 재확인(FOR UPDATE) → 새 revision 저장 → SUCCEEDED 마킹 →
     * REVIEW_REQUIRED 면 검수 알림으로 종료 / PASS 면 케이스 메타 갱신 + ai_usage + 성공 알림.
     * <b>자동 분석 파이프라인은 여기서 실행하지 않는다</b> — PASS 인 경우 실행에 필요한 정보를
     * {@link PipelineHandoff} 로 반환하고, 실행 여부·시점은 호출부가 (트랜잭션 밖에서) 정한다.
     * REVIEW_REQUIRED·소유권 상실·마킹 경합이면 {@code null} 을 반환한다(파이프라인 대상 아님).
     */
    public PipelineHandoff finalizeSucceeded(ApplicationCaseExtraction extraction, ExtractionResult result) {
        return transactionTemplate.execute(status -> {
            if (extractionMapper.findRunningExtractionForUpdate(extraction.getId()) == null) {
                return null;
            }

            Long completedJobPostingId = result.sourcePosting().getId();
            Integer completedJobPostingRevision = result.sourcePosting().getRevision();
            String completedPostingText = defaultText(
                    result.extractedPosting().extractedText(),
                    result.extractedPosting().originalText(),
                    null);
            if (result.saveExtractedPosting()) {
                JobPostingResponse savedPosting = jobPostingService.saveExtractedJobPosting(
                        extraction.getUserId(),
                        extraction.getApplicationCaseId(),
                        result.extractedPosting());
                completedJobPostingId = savedPosting.id();
                completedJobPostingRevision = savedPosting.revision();
                completedPostingText = defaultText(
                        savedPosting.extractedText(),
                        savedPosting.originalText(),
                        completedPostingText);
            }

            QualityGateResult quality = result.quality();
            if (extractionMapper.markExtractionSucceeded(
                    extraction.getId(),
                    completedJobPostingId,
                    quality.extractionStrategy(),
                    quality.qualityScore(),
                    quality.qualityStatus(),
                    quality.qualityReportJson(),
                    quality.modelVersionsJson(),
                    quality.fallbackEligible(),
                    quality.fallbackReason()) != 1) {
                status.setRollbackOnly();
                return null;
            }

            if (result.requiresReview()) {
                notificationService.notify(reviewRequiredNotification(extraction));
                return null;
            }

            ApplicationCase applicationCase = requireApplicationCase(extraction);
            applicationCaseMapper.updateApplicationCase(updatedApplicationCase(
                    applicationCase,
                    result.metadata()));
            if (result.metadata() != null && result.metadata().usage() != null) {
                aiUsageLogService.recordSuccess(
                        extraction.getUserId(),
                        extraction.getApplicationCaseId(),
                        FEATURE_JOB_POSTING_METADATA,
                        AiUsage.from(result.metadata().usage()));
            }
            notificationService.notify(successNotification(extraction));
            return new PipelineHandoff(completedJobPostingId, completedJobPostingRevision, completedPostingText);
        });
    }

    /**
     * 실패 종결(짧은 트랜잭션): FAILED 마킹(RUNNING 행만) + 실패 알림. 마킹이 실제로 이뤄진 경우에만
     * {@code postFailWithinTx} 훅을 같은 트랜잭션 안에서 실행한다(워커=초기 실행 프로필 abandon,
     * strict=아무것도 안 함). 반환값은 마킹 성공 여부.
     */
    public boolean finalizeFailed(ApplicationCaseExtraction extraction,
                                  RuntimeException ex,
                                  PostFailureAction postFailWithinTx) {
        String errorMessage = truncate(errorMessage(ex), MAX_ERROR_MESSAGE_LENGTH);
        QualityGateResult quality = qualityResult(ex);
        return doFinalizeFailed(extraction, errorMessage, quality, postFailWithinTx);
    }

    /** stale RUNNING 만료 종결 — 고정 타임아웃 메시지 + 품질 정보 없음. 나머지는 {@link #finalizeFailed} 와 동일. */
    public boolean finalizeStaleFailed(ApplicationCaseExtraction extraction,
                                       long timeoutMinutes,
                                       PostFailureAction postFailWithinTx) {
        String errorMessage = "Extraction job timed out after %d minutes.".formatted(Math.max(1, timeoutMinutes));
        return doFinalizeFailed(extraction, errorMessage, null, postFailWithinTx);
    }

    private boolean doFinalizeFailed(ApplicationCaseExtraction extraction,
                                     String errorMessage,
                                     QualityGateResult quality,
                                     PostFailureAction postFailWithinTx) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            if (extractionMapper.markExtractionFailed(
                    extraction.getId(),
                    errorMessage,
                    quality == null ? null : quality.extractionStrategy(),
                    quality == null ? null : quality.qualityScore(),
                    quality == null ? null : quality.qualityStatus(),
                    quality == null ? null : quality.qualityReportJson(),
                    quality == null ? null : quality.modelVersionsJson(),
                    quality != null && quality.fallbackEligible(),
                    quality == null ? null : quality.fallbackReason()) != 1) {
                return false;
            }
            notificationService.notify(failureNotification(extraction, errorMessage));
            postFailWithinTx.run(extraction.getApplicationCaseId(), errorMessage);
            return true;
        }));
    }

    /**
     * 공고 메타(회사·직무·마감) 추출 — 구조화 LLM 우선, 미설정/실패 시 regex 로 degrade.
     * ⚠️ 부가 정보 보강이라 <b>절대 추출 성공을 실패로 바꾸지 않는다</b>: LLM 예외는 여기서 catch 해 regex 로 폴백하고,
     * 예외가 {@code evaluate()} 밖으로 새지 않는다(fail-open).
     */
    private JobPostingMetadataPayload resolveJobPostingMetadata(String postingText) {
        JobPostingMetadataPayload regex = extractJobPostingMetadataByRegex(postingText);
        if (openAiClient == null || !openAiClient.configured()) {
            return regex;
        }
        try {
            JobPostingMetadataPayload llm = openAiClient.extractJobPostingMetadata(postingText);
            // LLM 우선, null 필드는 regex 로 보완(마감일도 LLM 우선). postingDate 는 항상 null.
            return new JobPostingMetadataPayload(
                    defaultText(llm.companyName(), regex.companyName(), null),
                    defaultText(llm.jobTitle(), regex.jobTitle(), null),
                    null,
                    llm.deadlineDate() != null ? llm.deadlineDate() : regex.deadlineDate(),
                    llm.usage());
        } catch (RuntimeException ex) {
            log.warn("공고 메타데이터 LLM 추출 실패 → regex 폴백: {}", rootCauseMessage(ex));
            return regex;
        }
    }

    private JobPostingMetadataPayload extractJobPostingMetadataByRegex(String postingText) {
        Matcher hiringMatcher = ENGLISH_HIRING_PATTERN.matcher(postingText);
        String companyName = null;
        String jobTitle = null;
        if (hiringMatcher.find()) {
            companyName = cleanMetadataText(hiringMatcher.group(1));
            jobTitle = cleanMetadataText(hiringMatcher.group(2));
        }
        companyName = defaultText(companyName, findLabeledLine(postingText, "Company", "회사", "기업명"), null);
        jobTitle = defaultText(jobTitle, findLabeledLine(postingText, "Role", "Position", "직무", "포지션"), null);
        return new JobPostingMetadataPayload(
                companyName,
                jobTitle,
                null,
                findDeadlineDate(postingText),
                null);
    }

    private ApplicationCase requireApplicationCase(ApplicationCaseExtraction extraction) {
        ApplicationCase applicationCase = applicationCaseMapper.findApplicationCaseByIdAndUserId(
                extraction.getApplicationCaseId(),
                extraction.getUserId());
        if (applicationCase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
        }
        return applicationCase;
    }

    private ApplicationCase updatedApplicationCase(ApplicationCase existing, JobPostingMetadataPayload metadata) {
        return ApplicationCase.builder()
                .id(existing.getId())
                .userId(existing.getUserId())
                .companyName(defaultText(metadata == null ? null : metadata.companyName(),
                        existing.getCompanyName(),
                        DEFAULT_COMPANY_NAME))
                .jobTitle(defaultText(metadata == null ? null : metadata.jobTitle(),
                        existing.getJobTitle(),
                        DEFAULT_JOB_TITLE))
                .postingDate(existing.getPostingDate())
                .deadlineDate(defaultDate(metadata == null ? null : metadata.deadlineDate(), existing.getDeadlineDate()))
                .sourceType(existing.getSourceType())
                .status(existing.getStatus())
                .favorite(existing.isFavorite())
                .archivedAt(existing.getArchivedAt())
                .build();
    }

    private boolean shouldSaveExtractedPosting(ApplicationCaseExtraction extraction,
                                               JobPosting posting,
                                               ExtractedPosting extractedPosting) {
        String sourceType = normalizeSourceType(extraction.getSourceType());
        if ("TEXT".equals(sourceType) || "MANUAL".equals(sourceType)) {
            return false;
        }
        if (isBlank(extractedPosting.extractedText())) {
            return false;
        }
        return !sameText(posting.getOriginalText(), extractedPosting.originalText())
                || !sameText(posting.getUploadedFileUrl(), extractedPosting.uploadedFileUrl())
                || !sameText(posting.getExtractedText(), extractedPosting.extractedText())
                || !sameText(posting.getSourceType(), extractedPosting.sourceType());
    }

    private Notification successNotification(ApplicationCaseExtraction extraction) {
        return Notification.builder()
                .userId(extraction.getUserId())
                .type(SUCCESS_NOTIFICATION_TYPE)
                .targetType(NOTIFICATION_TARGET_TYPE)
                .targetId(extraction.getApplicationCaseId())
                .title("공고 추출이 완료되었습니다.")
                .message("지원 건의 공고 정보가 업데이트되었습니다.")
                .link(overviewLink(extraction.getApplicationCaseId()))
                .read(false)
                .build();
    }

    private Notification failureNotification(ApplicationCaseExtraction extraction, String errorMessage) {
        return Notification.builder()
                .userId(extraction.getUserId())
                .type(FAILURE_NOTIFICATION_TYPE)
                .targetType(NOTIFICATION_TARGET_TYPE)
                .targetId(extraction.getApplicationCaseId())
                .title("공고 추출에 실패했습니다.")
                .message(errorMessage)
                .link(overviewLink(extraction.getApplicationCaseId()))
                .read(false)
                .build();
    }

    private Notification reviewRequiredNotification(ApplicationCaseExtraction extraction) {
        return Notification.builder()
                .userId(extraction.getUserId())
                .type(REVIEW_NOTIFICATION_TYPE)
                .targetType(NOTIFICATION_TARGET_TYPE)
                .targetId(extraction.getApplicationCaseId())
                .title("Job posting extraction needs review.")
                .message("Extracted posting text needs user review before automatic analysis continues.")
                .link(overviewLink(extraction.getApplicationCaseId()))
                .read(false)
                .build();
    }

    private static String overviewLink(Long applicationCaseId) {
        return "/applications/%d/overview".formatted(applicationCaseId);
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

    private static LocalDate defaultDate(LocalDate primary, LocalDate fallback) {
        return primary != null ? primary : fallback;
    }

    private static String findLabeledLine(String text, String... labels) {
        if (isBlank(text) || labels == null || labels.length == 0) {
            return null;
        }
        for (String line : text.split("\\R")) {
            for (String label : labels) {
                String value = valueAfterLabel(line, label);
                if (!isBlank(value)) {
                    return cleanMetadataText(value);
                }
            }
        }
        return null;
    }

    private static String valueAfterLabel(String line, String label) {
        if (line == null || label == null) {
            return null;
        }
        String trimmed = line.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String normalizedLabel = label.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(normalizedLabel)) {
            return null;
        }
        String value = trimmed.substring(label.length()).trim();
        if (value.startsWith(":") || value.startsWith("-") || value.startsWith("：")) {
            value = value.substring(1).trim();
        }
        return value;
    }

    private static LocalDate findDeadlineDate(String text) {
        if (isBlank(text)) {
            return null;
        }
        Matcher matcher = DEADLINE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String cleanMetadataText(String value) {
        if (isBlank(value)) {
            return null;
        }
        String cleaned = value.trim();
        int sentenceEnd = cleaned.indexOf('.');
        if (sentenceEnd > 0) {
            cleaned = cleaned.substring(0, sentenceEnd).trim();
        }
        if (cleaned.length() > 255) {
            cleaned = cleaned.substring(0, 255).trim();
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String errorMessage(Throwable ex) {
        if (!isBlank(ex.getMessage())) {
            return ex.getMessage();
        }
        return ex.getClass().getSimpleName();
    }

    private static String rootCauseMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return errorMessage(current).replace('\n', ' ').replace('\r', ' ');
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static boolean sameText(String left, String right) {
        if (isBlank(left) && isBlank(right)) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equals(right.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static QualityGateResult qualityResult(RuntimeException ex) {
        if (ex instanceof ExtractionQualityFailedException qualityException) {
            return qualityException.quality();
        }
        return null;
    }

    private static QualityGateResult qualityFromExtractedPosting(ExtractedPosting extractedPosting) {
        if (extractedPosting == null || isBlank(extractedPosting.qualityStatus())) {
            return null;
        }
        String qualityStatus = normalizeQualityStatus(extractedPosting.qualityStatus());
        if (qualityStatus == null) {
            return new QualityGateResult(
                    extractedPosting.extractionStrategy(),
                    extractedPosting.qualityScore() == null ? 0 : extractedPosting.qualityScore(),
                    ApplicationCaseExtractionQualityGate.QUALITY_FAILED,
                    extractedPosting.qualityReportJson(),
                    extractedPosting.modelVersionsJson(),
                    extractedPosting.fallbackEligible(),
                    INVALID_WORKER_QUALITY_STATUS_REASON + " value=" + extractedPosting.qualityStatus());
        }
        return new QualityGateResult(
                extractedPosting.extractionStrategy(),
                extractedPosting.qualityScore() == null ? 0 : extractedPosting.qualityScore(),
                qualityStatus,
                extractedPosting.qualityReportJson(),
                extractedPosting.modelVersionsJson(),
                extractedPosting.fallbackEligible(),
                extractedPosting.fallbackReason());
    }

    private static String normalizeQualityStatus(String qualityStatus) {
        if (isBlank(qualityStatus)) {
            return null;
        }
        String normalized = qualityStatus.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case ApplicationCaseExtractionQualityGate.QUALITY_PASS,
                    ApplicationCaseExtractionQualityGate.QUALITY_REVIEW_REQUIRED,
                    ApplicationCaseExtractionQualityGate.QUALITY_FAILED -> normalized;
            default -> null;
        };
    }

    /** 성공 종결 뒤 (트랜잭션 밖에서) 자동 분석 파이프라인을 돌리는 데 필요한 최소 정보. */
    public record PipelineHandoff(Long jobPostingId, Integer jobPostingRevision, String postingText) {
    }

    public record ExtractionResult(
            JobPosting sourcePosting,
            ExtractedPosting extractedPosting,
            JobPostingMetadataPayload metadata,
            boolean saveExtractedPosting,
            QualityGateResult quality,
            boolean requiresReview
    ) {
    }

    /** 실패 종결 트랜잭션 안에서 실행할 후처리 훅(워커=프로필 abandon, strict=no-op). */
    @FunctionalInterface
    public interface PostFailureAction {
        PostFailureAction NONE = (applicationCaseId, errorMessage) -> { };

        void run(Long applicationCaseId, String errorMessage);
    }

    static final class ExtractionQualityFailedException extends RuntimeException {
        private final QualityGateResult quality;

        ExtractionQualityFailedException(QualityGateResult quality, String message) {
            super(message);
            this.quality = quality;
        }

        QualityGateResult quality() {
            return quality;
        }
    }
}
