package com.careertuner.applicationcase.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
import com.careertuner.jobposting.mapper.JobPostingMapper;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationCaseExtractionWorker {

    private static final int BATCH_SIZE = 5;
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
    private final JobPostingMapper jobPostingMapper;
    private final JobPostingService jobPostingService;
    private final ApplicationCaseExtractionQualityGate qualityGate;
    private final OpenAiResponsesClient openAiClient;
    private final AiUsageLogService aiUsageLogService;
    private final ApplicationCaseAutoPipelineService autoPipelineService;
    private final NotificationService notificationService;
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
            if (completeStaleFailed(extraction)) {
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
            completeSucceeded(extraction, result);
        } catch (RuntimeException ex) {
            log.warn("Application case extraction failed. extractionId={}", extraction.getId(), ex);
            completeFailed(extraction, ex);
        }
    }

    private ExtractionResult extractAndAnalyze(ApplicationCaseExtraction extraction) {
        JobPosting posting = requireJobPosting(extraction);
        ExtractedPosting extractedPosting = extractPostingText(extraction, posting);
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
        JobPostingMetadataPayload metadata = extractJobPostingMetadata(postingText);
        return new ExtractionResult(posting, extractedPosting, metadata, saveExtractedPosting, quality, false);
    }

    private JobPostingMetadataPayload extractJobPostingMetadata(String postingText) {
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

    /** 선커밋(짧은 트랜잭션) 결과 — LLM 파이프라인은 커밋 뒤 트랜잭션 밖에서 이 값으로 실행한다. */
    private record PipelineHandoff(Long jobPostingId, Integer jobPostingRevision, String postingText) {
    }

    private void completeSucceeded(ApplicationCaseExtraction extraction, ExtractionResult result) {
        // 짧은 트랜잭션: SUCCEEDED 마킹 + case 메타 + 알림까지 선커밋 → 타 커넥션(챗봇 폴링·목록 조회)에
        // 즉시 가시화. LLM 파이프라인(직무·기업분석 등 80~120초)은 커밋 뒤 트랜잭션 밖에서 실행 —
        // 트랜잭션이 LLM 시간 동안 열려 있어 SUCCEEDED 가 ~2분 불가시하던 문제(실측 case 65·66) 해소.
        PipelineHandoff handoff = transactionTemplate.execute(status -> {
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
                        result.metadata().usage());
            }
            notificationService.notify(successNotification(extraction));
            return new PipelineHandoff(completedJobPostingId, completedJobPostingRevision, completedPostingText);
        });
        if (handoff == null) {
            // 소유권 상실(stale)·마킹 경합·review-required — 기존과 동일하게 파이프라인 미실행.
            return;
        }
        // 파이프라인 실패 처리는 runAfterExtractionPass 내부 catch(상태 복원 + FAILED usage log) 그대로 —
        // 분리 전에도 내부 catch 여서 전체 롤백이 아니었고(부분 산출물 커밋), 분리 후에도 같은 의미론이다.
        // 무트랜잭션 실행이라 내부의 ANALYZING→READY 전이가 이제 단계별로 실시간 노출된다.
        autoPipelineService.runAfterExtractionPass(
                extraction.getUserId(),
                extraction.getApplicationCaseId(),
                handoff.jobPostingId(),
                handoff.jobPostingRevision(),
                handoff.postingText());
    }

    private void completeFailed(ApplicationCaseExtraction extraction, RuntimeException ex) {
        transactionTemplate.execute(status -> {
            String errorMessage = truncate(errorMessage(ex), MAX_ERROR_MESSAGE_LENGTH);
            QualityGateResult quality = qualityResult(ex);
            if (extractionMapper.markExtractionFailed(
                    extraction.getId(),
                    errorMessage,
                    quality == null ? null : quality.extractionStrategy(),
                    quality == null ? null : quality.qualityScore(),
                    quality == null ? null : quality.qualityStatus(),
                    quality == null ? null : quality.qualityReportJson(),
                    quality == null ? null : quality.modelVersionsJson(),
                    quality != null && quality.fallbackEligible(),
                    quality == null ? null : quality.fallbackReason()) == 1) {
                notificationService.notify(failureNotification(extraction, errorMessage));
            }
            return null;
        });
    }

    private boolean completeStaleFailed(ApplicationCaseExtraction extraction) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            String errorMessage = "Extraction job timed out after %d minutes.".formatted(Math.max(1, runningTimeoutMinutes));
            if (extractionMapper.markExtractionFailed(
                    extraction.getId(),
                    errorMessage,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null) != 1) {
                return false;
            }
            notificationService.notify(failureNotification(extraction, errorMessage));
            return true;
        }));
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

    private ApplicationCase requireApplicationCase(ApplicationCaseExtraction extraction) {
        ApplicationCase applicationCase = applicationCaseMapper.findApplicationCaseByIdAndUserId(
                extraction.getApplicationCaseId(),
                extraction.getUserId());
        if (applicationCase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
        }
        return applicationCase;
    }

    private ExtractedPosting extractPostingText(ApplicationCaseExtraction extraction, JobPosting posting) {
        String sourceType = normalizeSourceType(extraction.getSourceType());
        return switch (sourceType) {
            case "URL" -> jobPostingService.extractUrlJobPosting(requiredText(posting.getUploadedFileUrl(), "uploadedFileUrl"));
            case "PDF", "IMAGE" -> jobPostingService.extractUploadedJobPosting(
                    extraction.getUserId(),
                    extraction.getApplicationCaseId(),
                    sourceType,
                    requiredText(posting.getUploadedFileUrl(), "uploadedFileUrl"));
            case "TEXT", "MANUAL" -> new ExtractedPosting(
                    sourceType,
                    posting.getUploadedFileUrl(),
                    posting.getOriginalText(),
                    sourceText(posting),
                    null);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "sourceType 값이 올바르지 않습니다.");
        };
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

    private static String sourceText(ExtractedPosting extractedPosting) {
        return requiredText(defaultText(extractedPosting.extractedText(), extractedPosting.originalText(), null), "postingText");
    }

    private static String sourceText(JobPosting posting) {
        return requiredText(defaultText(posting.getExtractedText(), posting.getOriginalText(), null), "postingText");
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

    private record ExtractionResult(
            JobPosting sourcePosting,
            ExtractedPosting extractedPosting,
            JobPostingMetadataPayload metadata,
            boolean saveExtractedPosting,
            QualityGateResult quality,
            boolean requiresReview
    ) {
    }

    private static final class ExtractionQualityFailedException extends RuntimeException {
        private final QualityGateResult quality;

        private ExtractionQualityFailedException(QualityGateResult quality, String message) {
            super(message);
            this.quality = quality;
        }

        private QualityGateResult quality() {
            return quality;
        }
    }
}
