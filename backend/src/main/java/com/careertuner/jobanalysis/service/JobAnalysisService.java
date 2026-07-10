package com.careertuner.jobanalysis.service;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.ApplicationCaseAnalysisStatusService;
import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.applicationcase.service.BAnalysisGenerationService;
import com.careertuner.applicationcase.service.BAnalysisGenerationService.GeneratedJobAnalysis;
import com.careertuner.applicationcase.service.BAnalysisJsonValidator;
import com.careertuner.applicationcase.support.BDisplayTime;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobanalysis.domain.JobAnalysis;
import com.careertuner.jobanalysis.dto.JobAnalysisReviewRequest;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobAnalysisService {

    private static final String FEATURE_JOB_ANALYSIS = "JOB_ANALYSIS";

    private final ApplicationCaseAccessService accessService;
    private final JobAnalysisMapper jobAnalysisMapper;
    private final BAnalysisGenerationService bAnalysisGenerationService;
    private final AiUsageLogService aiUsageLogService;
    private final ApplicationCaseAnalysisStatusService statusService;
    private final TransactionTemplate transactionTemplate;
    private final BAnalysisJsonValidator analysisJsonValidator;
    private final NotificationService notificationService;

    public JobAnalysisResponse createJobAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = accessService.requireOwned(userId, applicationCaseId);
        ensureAnalysisRunnable(applicationCase.getStatus());
        JobPosting jobPosting = accessService.latestPostingRequired(applicationCaseId);
        String sourceText = accessService.sourceText(jobPosting);
        String previousStatus = applicationCase.getStatus();
        statusService.markAnalyzing(userId, applicationCaseId, previousStatus);
        try {
            GeneratedJobAnalysis generated = bAnalysisGenerationService.generateJobAnalysis(applicationCase, sourceText);
            var payload = generated.payload();
            return transactionTemplate.execute(status -> {
                JobAnalysis jobAnalysis = JobAnalysis.builder()
                        .applicationCaseId(applicationCaseId)
                        .jobPostingId(jobPosting.getId())
                        .jobPostingRevision(jobPosting.getRevision())
                        .employmentType(blankToNull(payload.employmentType()))
                        .experienceLevel(blankToNull(payload.experienceLevel()))
                        .requiredSkills(payload.requiredSkills())
                        .preferredSkills(payload.preferredSkills())
                        .duties(blankToNull(payload.duties()))
                        .qualifications(blankToNull(payload.qualifications()))
                        .difficulty(payload.difficulty())
                        .summary(blankToNull(payload.summary()))
                        .evidence(payload.evidence())
                        .ambiguousConditions(payload.ambiguousConditions())
                        .build();
                jobAnalysisMapper.insertJobAnalysis(jobAnalysis);
                JobAnalysisResponse response = toResponse(jobAnalysisMapper.findLatestJobAnalysisByCaseId(applicationCaseId));
                statusService.markReadyAfterAnalysis(userId, applicationCaseId, previousStatus);
                if (generated.fellBack()) {
                    aiUsageLogService.recordFailure(
                            userId,
                            applicationCaseId,
                            FEATURE_JOB_ANALYSIS,
                            generated.fallbackAttemptedModel(),
                            generated.fallbackReason());
                }
                aiUsageLogService.recordLocalSuccess(userId, applicationCaseId, FEATURE_JOB_ANALYSIS, payload.usage());
                // 공고 분석이 성공하면 사용자에게 완료 알림을 남긴다.
                notificationService.notify(Notification.builder()
                        .userId(userId)
                        .type("JOB_ANALYSIS_COMPLETE")
                        .targetType("APPLICATION_CASE")
                        .targetId(applicationCaseId)
                        .title("공고 분석이 완료되었습니다")
                        .message("%s · %s 공고 분석 결과가 준비되었습니다.".formatted(
                                applicationCase.getCompanyName(), applicationCase.getJobTitle()))
                        .link("/applications/" + applicationCaseId + "/job-analysis")
                        .build());
                return response;
            });
        } catch (RuntimeException ex) {
            restorePreviousStatus(userId, applicationCaseId, previousStatus, ex);
            aiUsageLogService.recordFailure(userId, applicationCaseId, FEATURE_JOB_ANALYSIS, userFacingFailureMessage(ex, "공고 분석 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public JobAnalysisResponse getJobAnalysis(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        return toResponse(jobAnalysisMapper.findLatestJobAnalysisByCaseId(applicationCaseId));
    }

    @Transactional(readOnly = true)
    public List<JobAnalysisResponse> getJobAnalysisHistory(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        return jobAnalysisMapper.findJobAnalysisHistoryByCaseId(applicationCaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public JobAnalysisResponse reviewJobAnalysis(Long userId, Long applicationCaseId, Long analysisId, JobAnalysisReviewRequest request) {
        accessService.requireOwned(userId, applicationCaseId);
        JobAnalysis existing = jobAnalysisMapper.findJobAnalysisByIdAndCaseId(analysisId, applicationCaseId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고 분석을 찾을 수 없습니다.");
        }

        JobAnalysis updated = JobAnalysis.builder()
                .id(existing.getId())
                .applicationCaseId(existing.getApplicationCaseId())
                .jobPostingId(existing.getJobPostingId())
                .jobPostingRevision(existing.getJobPostingRevision())
                .employmentType(defaultString(request.employmentType(), existing.getEmploymentType()))
                .experienceLevel(defaultString(request.experienceLevel(), existing.getExperienceLevel()))
                .requiredSkills(defaultString(request.requiredSkills(), existing.getRequiredSkills()))
                .preferredSkills(defaultString(request.preferredSkills(), existing.getPreferredSkills()))
                .duties(defaultString(request.duties(), existing.getDuties()))
                .qualifications(defaultString(request.qualifications(), existing.getQualifications()))
                .difficulty(defaultString(request.difficulty(), existing.getDifficulty()))
                .summary(defaultString(request.summary(), existing.getSummary()))
                .evidence(defaultValidatedJson(request.evidence(), existing.getEvidence(), analysisJsonValidator::validateEvidence))
                .ambiguousConditions(defaultValidatedJson(
                        request.ambiguousConditions(),
                        existing.getAmbiguousConditions(),
                        analysisJsonValidator::validateAmbiguousConditions))
                .confirmedAt(Boolean.TRUE.equals(request.confirmed()) ? BDisplayTime.now() : existing.getConfirmedAt())
                .adminMemo(existing.getAdminMemo())
                .build();
        jobAnalysisMapper.updateJobAnalysisReview(updated);
        return toResponse(jobAnalysisMapper.findJobAnalysisByIdAndCaseId(analysisId, applicationCaseId));
    }

    /** created_at 은 DB CURRENT_TIMESTAMP(UTC)로 저장된다. 화면(KST) 표시를 위해 응답 직전 UTC→KST 로 보정한다. */
    private JobAnalysisResponse toResponse(JobAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        analysis.setCreatedAt(BDisplayTime.dbToDisplay(analysis.getCreatedAt()));
        return JobAnalysisResponse.from(analysis);
    }

    private void restorePreviousStatus(Long userId, Long applicationCaseId, String previousStatus, RuntimeException ex) {
        try {
            statusService.restorePreviousStatus(userId, applicationCaseId, previousStatus);
        } catch (RuntimeException statusException) {
            ex.addSuppressed(statusException);
        }
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static String defaultValidatedJson(String value, String defaultValue, Function<String, String> validator) {
        if (isBlank(value)) {
            return defaultValue;
        }
        return validator.apply(value.trim());
    }

    private static void ensureAnalysisRunnable(String status) {
        if ("ANALYZING".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 분석이 진행 중입니다. 잠시 후 결과를 확인해 주세요.");
        }
        if (!"DRAFT".equals(status) && !"READY".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "현재 상태에서는 분석을 다시 실행할 수 없습니다.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String userFacingFailureMessage(RuntimeException ex, String fallback) {
        String message = ex.getMessage();
        if (isBlank(message)) {
            return fallback;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("### error")
                || lower.contains("sql:")
                || lower.contains("com.mysql")
                || lower.contains("org.springframework")
                || lower.contains("statement cancelled")
                || lower.contains("timeoutexception")) {
            return fallback;
        }
        return message.length() > 300 ? fallback : message;
    }

}
