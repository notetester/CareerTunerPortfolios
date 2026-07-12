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
import com.careertuner.applicationcase.service.BAnalysisGenerationService.StrictJobResult;
import com.careertuner.applicationcase.service.BAnalysisProvider;
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
        String previousStatus = accessService.requireOwned(userId, applicationCaseId).getStatus();
        ensureAnalysisRunnable(previousStatus);
        // 배타 획득(케이스 행 잠금 + 활성 추출 검사 + ANALYZING CAS) 뒤에 분석 입력 전체(지원 건 메타데이터 +
        // 최신 공고)를 다시 읽는다 — 게이트 앞 스냅샷이면 그 사이 끝난 재추출이 갱신한 기업명·직무명·revision 을
        // 놓친다(입력 스냅샷 직렬화). AutoPrep 등 비-strict 호출도 이 경로라 같은 상호 배제를 받는다.
        // 획득 후 조회 실패는 catch 가 상태를 복원한다. previousStatus 는 CAS 비교값이라 pre-read 로 충분하다.
        statusService.markAnalyzingExclusive(userId, applicationCaseId, previousStatus);
        try {
            ApplicationCase applicationCase = accessService.requireOwned(userId, applicationCaseId);
            JobPosting jobPosting = accessService.latestPostingRequired(applicationCaseId);
            String sourceText = accessService.sourceText(jobPosting);
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

    /**
     * strict 수동 재분석 — 고른 provider 하나로만 공고 분석을 다시 실행한다(자동 체인·self-rules 미사용).
     * 성공 시 provenance(선택=실제 provider·모델·attempt_path·run_mode=MANUAL·fallback_used=false)를 함께 저장하고,
     * 실패 시 <b>기존 분석을 보존</b>한 채 상태만 되돌리고 예외를 던진다(안전망 없음 = 실패는 실패).
     * 자동 {@link #createJobAnalysis(Long, Long)} 와 구조는 같지만 생성 경로·provenance 만 다르다
     * (두 경로 모두 배타 획득 + 획득 후 공고 조회를 공유한다).
     */
    public JobAnalysisResponse createJobAnalysisStrict(Long userId, Long applicationCaseId, BAnalysisProvider provider) {
        String previousStatus = accessService.requireOwned(userId, applicationCaseId).getStatus();
        ensureAnalysisRunnable(previousStatus);
        // 배타 획득(케이스 행 잠금 + 활성 추출 검사 + ANALYZING CAS) — strict 재추출과 직렬화. 분석 입력
        // 전체(지원 건 메타데이터 + 최신 공고)는 반드시 획득 <b>뒤에</b> 읽는다(게이트 앞 스냅샷이면 그 사이
        // 끝난 재추출이 갱신한 기업명·직무명·revision 을 놓친다).
        statusService.markAnalyzingExclusive(userId, applicationCaseId, previousStatus);
        try {
            ApplicationCase applicationCase = accessService.requireOwned(userId, applicationCaseId);
            JobPosting jobPosting = accessService.latestPostingRequired(applicationCaseId);
            String sourceText = accessService.sourceText(jobPosting);
            StrictJobResult strict = bAnalysisGenerationService.generateJobAnalysisStrict(applicationCase, sourceText, provider);
            var payload = strict.payload();
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
                        .requestedProvider(provider.name())
                        .actualProvider(provider.name())
                        .actualModel(payload.usage() == null ? null : payload.usage().model())
                        .fallbackUsed(false)
                        .attemptPath(BAnalysisProvider.toAttemptPathJson(strict.attempts()))
                        .runMode("MANUAL")
                        .build();
                jobAnalysisMapper.insertJobAnalysis(jobAnalysis);
                JobAnalysisResponse response = toResponse(jobAnalysisMapper.findLatestJobAnalysisByCaseId(applicationCaseId));
                statusService.markReadyAfterAnalysis(userId, applicationCaseId, previousStatus);
                aiUsageLogService.recordLocalSuccess(userId, applicationCaseId, FEATURE_JOB_ANALYSIS, payload.usage());
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
