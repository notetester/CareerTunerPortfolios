package com.careertuner.applicationcase.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobPostingMetadataPayload;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.mapper.JobPostingMapper;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.mapper.NotificationMapper;

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
    private static final String FEATURE_JOB_POSTING_METADATA = "JOB_POSTING_METADATA";

    private final ApplicationCaseExtractionMapper extractionMapper;
    private final ApplicationCaseMapper applicationCaseMapper;
    private final JobPostingMapper jobPostingMapper;
    private final JobPostingService jobPostingService;
    private final OpenAiResponsesClient openAiClient;
    private final AiUsageLogService aiUsageLogService;
    private final NotificationMapper notificationMapper;
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
        LocalDateTime startedBefore = LocalDateTime.now().minusMinutes(Math.max(1, runningTimeoutMinutes));
        List<ApplicationCaseExtraction> staleExtractions = extractionMapper.findStaleRunningExtractions(
                startedBefore,
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
        String postingText = sourceText(extractedPosting);
        JobPostingMetadataPayload metadata = extractJobPostingMetadata(extraction, postingText);
        boolean saveExtractedPosting = shouldSaveExtractedPosting(extraction, posting, extractedPosting);
        return new ExtractionResult(posting, extractedPosting, metadata, saveExtractedPosting);
    }

    private JobPostingMetadataPayload extractJobPostingMetadata(ApplicationCaseExtraction extraction, String postingText) {
        try {
            return openAiClient.extractJobPostingMetadata(postingText);
        } catch (RuntimeException ex) {
            aiUsageLogService.recordFailure(
                    extraction.getUserId(),
                    extraction.getApplicationCaseId(),
                    FEATURE_JOB_POSTING_METADATA,
                    errorMessage(ex));
            throw ex;
        }
    }

    private void completeSucceeded(ApplicationCaseExtraction extraction, ExtractionResult result) {
        transactionTemplate.execute(status -> {
            if (extractionMapper.findRunningExtractionForUpdate(extraction.getId()) == null) {
                return null;
            }

            Long completedJobPostingId = result.sourcePosting().getId();
            if (result.saveExtractedPosting()) {
                JobPostingResponse savedPosting = jobPostingService.saveExtractedJobPosting(
                        extraction.getUserId(),
                        extraction.getApplicationCaseId(),
                        result.extractedPosting());
                completedJobPostingId = savedPosting.id();
            }

            if (extractionMapper.markExtractionSucceeded(extraction.getId(), completedJobPostingId) != 1) {
                status.setRollbackOnly();
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
            notificationMapper.insert(successNotification(extraction));
            return null;
        });
    }

    private void completeFailed(ApplicationCaseExtraction extraction, RuntimeException ex) {
        transactionTemplate.execute(status -> {
            String errorMessage = truncate(errorMessage(ex), MAX_ERROR_MESSAGE_LENGTH);
            if (extractionMapper.markExtractionFailed(extraction.getId(), errorMessage) == 1) {
                notificationMapper.insert(failureNotification(extraction, errorMessage));
            }
            return null;
        });
    }

    private boolean completeStaleFailed(ApplicationCaseExtraction extraction) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            String errorMessage = "Extraction job timed out after %d minutes.".formatted(Math.max(1, runningTimeoutMinutes));
            if (extractionMapper.markExtractionFailed(extraction.getId(), errorMessage) != 1) {
                return false;
            }
            notificationMapper.insert(failureNotification(extraction, errorMessage));
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

    private record ExtractionResult(
            JobPosting sourcePosting,
            ExtractedPosting extractedPosting,
            JobPostingMetadataPayload metadata,
            boolean saveExtractedPosting
    ) {
    }
}
