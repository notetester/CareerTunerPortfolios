package com.careertuner.companyanalysis.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.applicationcase.service.ApplicationCaseAnalysisStatusService;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.applicationcase.service.BAnalysisGenerationService;
import com.careertuner.applicationcase.service.BAnalysisGenerationService.GeneratedCompanyAnalysis;
import com.careertuner.applicationcase.service.BAnalysisJsonValidator;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.dto.CompanyAnalysisReviewRequest;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.jobposting.domain.JobPosting;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyAnalysisService {

    private static final String FEATURE_COMPANY_RESEARCH = "COMPANY_RESEARCH";
    private static final int COMPANY_INDUSTRY_MAX_LENGTH = 100;

    private final ApplicationCaseAccessService accessService;
    private final CompanyAnalysisMapper companyAnalysisMapper;
    private final BAnalysisGenerationService bAnalysisGenerationService;
    private final AiUsageLogService aiUsageLogService;
    private final ApplicationCaseAnalysisStatusService statusService;
    private final TransactionTemplate transactionTemplate;
    private final BAnalysisJsonValidator analysisJsonValidator;
    private final BCompanyAnalysisCanonicalizer canonicalizer;

    public CompanyAnalysisResponse createCompanyAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = accessService.requireOwned(userId, applicationCaseId);
        ensureAnalysisRunnable(applicationCase.getStatus());
        JobPosting jobPosting = accessService.latestPostingRequired(applicationCaseId);
        String sourceText = accessService.sourceText(jobPosting);
        String previousStatus = applicationCase.getStatus();
        statusService.markAnalyzing(userId, applicationCaseId, previousStatus);
        try {
            GeneratedCompanyAnalysis generated = bAnalysisGenerationService.generateCompanyAnalysis(applicationCase, sourceText);
            // 자동 파이프라인 경로와 동일한 canonicalizer 로 저장 전 정규화한다
            // (evidence gate, ID/sourceKind/sourceRef 보정, unknowns 접기, sources 통일).
            var payload = canonicalizer.canonicalizeForStorage(
                    generated.payload(),
                    jobPosting.getId(),
                    jobPosting.getRevision(),
                    sourceText,
                    applicationCase.getCompanyName(),
                    applicationCase.getJobTitle()).payload();
            LocalDateTime checkedAt = LocalDateTime.now();
            return transactionTemplate.execute(status -> {
                CompanyAnalysis companyAnalysis = CompanyAnalysis.builder()
                        .applicationCaseId(applicationCaseId)
                        .jobPostingId(jobPosting.getId())
                        .jobPostingRevision(jobPosting.getRevision())
                        .companySummary(blankToNull(payload.companySummary()))
                        .recentIssues(blankToNull(payload.recentIssues()))
                        .industry(compactColumnText(payload.industry(), COMPANY_INDUSTRY_MAX_LENGTH))
                        .competitors(payload.competitors())
                        .interviewPoints(blankToNull(payload.interviewPoints()))
                        .sources(payload.sources())
                        .verifiedFacts(payload.verifiedFacts())
                        .aiInferences(payload.aiInferences())
                        .sourceType("JOB_POSTING")
                        .checkedAt(checkedAt)
                        .refreshRecommendedAt(checkedAt.plusDays(30))
                        .build();
                companyAnalysisMapper.insertCompanyAnalysis(companyAnalysis);
                CompanyAnalysisResponse response = toResponse(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(applicationCaseId));
                statusService.markReadyAfterAnalysis(userId, applicationCaseId, previousStatus);
                if (generated.fellBack()) {
                    aiUsageLogService.recordFailure(
                            userId,
                            applicationCaseId,
                            FEATURE_COMPANY_RESEARCH,
                            generated.fallbackAttemptedModel(),
                            generated.fallbackReason());
                }
                aiUsageLogService.recordLocalSuccess(userId, applicationCaseId, FEATURE_COMPANY_RESEARCH, payload.usage());
                return response;
            });
        } catch (RuntimeException ex) {
            restorePreviousStatus(userId, applicationCaseId, previousStatus, ex);
            aiUsageLogService.recordFailure(userId, applicationCaseId, FEATURE_COMPANY_RESEARCH, userFacingFailureMessage(ex, "기업 분석 결과 저장 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public CompanyAnalysisResponse getCompanyAnalysis(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        return toResponse(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(applicationCaseId));
    }

    @Transactional(readOnly = true)
    public List<CompanyAnalysisResponse> getCompanyAnalysisHistory(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        return companyAnalysisMapper.findCompanyAnalysisHistoryByCaseId(applicationCaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CompanyAnalysisResponse reviewCompanyAnalysis(Long userId, Long applicationCaseId, Long analysisId, CompanyAnalysisReviewRequest request) {
        accessService.requireOwned(userId, applicationCaseId);
        CompanyAnalysis existing = companyAnalysisMapper.findCompanyAnalysisByIdAndCaseId(analysisId, applicationCaseId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "기업 분석을 찾을 수 없습니다.");
        }

        CompanyAnalysis updated = CompanyAnalysis.builder()
                .id(existing.getId())
                .applicationCaseId(existing.getApplicationCaseId())
                .jobPostingId(existing.getJobPostingId())
                .jobPostingRevision(existing.getJobPostingRevision())
                .companySummary(defaultString(request.companySummary(), existing.getCompanySummary()))
                .recentIssues(defaultString(request.recentIssues(), existing.getRecentIssues()))
                .industry(compactColumnText(defaultString(request.industry(), existing.getIndustry()), COMPANY_INDUSTRY_MAX_LENGTH))
                .competitors(defaultString(request.competitors(), existing.getCompetitors()))
                .interviewPoints(defaultString(request.interviewPoints(), existing.getInterviewPoints()))
                .sources(defaultString(request.sources(), existing.getSources()))
                .verifiedFacts(defaultValidatedJson(request.verifiedFacts(), existing.getVerifiedFacts(), analysisJsonValidator::validateVerifiedFacts))
                .aiInferences(reviewedAiInferences(request.aiInferences(), existing.getAiInferences()))
                .sourceType(existing.getSourceType())
                .checkedAt(existing.getCheckedAt())
                .refreshRecommendedAt(existing.getRefreshRecommendedAt())
                .confirmedAt(Boolean.TRUE.equals(request.confirmed()) ? LocalDateTime.now() : existing.getConfirmedAt())
                .adminMemo(existing.getAdminMemo())
                .build();
        companyAnalysisMapper.updateCompanyAnalysisReview(updated);
        return toResponse(companyAnalysisMapper.findCompanyAnalysisByIdAndCaseId(analysisId, applicationCaseId));
    }

    /**
     * 응답 직전 unknowns 펼치기 — 저장된 aiInferences 의 {@code kind=UNKNOWN} 마커를 분리해
     * virtual unknowns 로 내리고, aiInferences 에서는 제거한다. 프런트/하네스가 마커를
     * 직접 파싱하지 않게 하는 단일 지점이다. 마커 없는 기존 레코드는 그대로 통과한다.
     */
    private CompanyAnalysisResponse toResponse(CompanyAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        return CompanyAnalysisResponse.from(
                analysis,
                canonicalizer.withoutUnknownMarkers(analysis.getAiInferences()),
                canonicalizer.extractUnknowns(analysis.getAiInferences()));
    }

    /**
     * 검수 저장용 aiInferences 병합 — 응답에서 마커가 제거된 채 편집되므로, 저장 시 기존 레코드의
     * unknown 마커를 서버가 재부착한다. 프런트 additive key 보존 여부와 무관하게 마커가
     * 유실되거나 일반 추론으로 오염되지 않는다(231 문서 5-7 릴리스 제약의 구조적 해소).
     */
    private String reviewedAiInferences(String requested, String existingJson) {
        if (isBlank(requested)) {
            return existingJson;
        }
        String validated = analysisJsonValidator.validateAiInferences(requested.trim());
        return canonicalizer.mergeUnknownMarkers(validated, existingJson);
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

    private static String compactColumnText(String value, int maxLength) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
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

    private void restorePreviousStatus(Long userId, Long applicationCaseId, String previousStatus, RuntimeException ex) {
        try {
            statusService.restorePreviousStatus(userId, applicationCaseId, previousStatus);
        } catch (RuntimeException statusException) {
            ex.addSuppressed(statusException);
        }
    }

}
