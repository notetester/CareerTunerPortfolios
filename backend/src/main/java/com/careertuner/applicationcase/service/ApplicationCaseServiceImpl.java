package com.careertuner.applicationcase.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;
import com.careertuner.applicationcase.domain.FitAnalysis;
import com.careertuner.applicationcase.dto.AnalysisResponse;
import com.careertuner.applicationcase.dto.AiUsageFailureResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseExtractionResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseFromJobPostingResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.dto.CreateApplicationCaseFromJobPostingRequest;
import com.careertuner.applicationcase.dto.CreateApplicationCaseRequest;
import com.careertuner.applicationcase.dto.FitAnalysisResponse;
import com.careertuner.applicationcase.dto.JobPostingMetadataResponse;
import com.careertuner.applicationcase.dto.UpdateApplicationCaseRequest;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.dto.CompanyAnalysisReviewRequest;
import com.careertuner.companyanalysis.service.CompanyAnalysisService;
import com.careertuner.jobanalysis.domain.JobAnalysis;
import com.careertuner.jobanalysis.dto.JobAnalysisReviewRequest;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;
import com.careertuner.jobanalysis.service.JobAnalysisService;
import com.careertuner.jobanalysis.service.JobAnalysisService.MockAnalysisSeed;
import com.careertuner.jobposting.dto.JobPostingRequest;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.service.JobPostingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApplicationCaseServiceImpl implements ApplicationCaseService {

    private static final String DEFAULT_SOURCE_TYPE = "TEXT";
    private static final String DEFAULT_STATUS = "DRAFT";
    private static final String DEFAULT_COMPANY_NAME = "기업명 확인 필요";
    private static final String DEFAULT_JOB_TITLE = "직무명 확인 필요";
    private static final String EXTRACTION_STATUS_QUEUED = "QUEUED";
    private static final String EXTRACTION_STATUS_FAILED = "FAILED";
    private static final Set<String> SOURCE_TYPES = Set.of("TEXT", "PDF", "IMAGE", "URL", "MANUAL");
    private static final Set<String> JOB_POSTING_JSON_SOURCE_TYPES = Set.of("TEXT", "MANUAL", "URL");
    private static final Set<String> JOB_POSTING_UPLOAD_SOURCE_TYPES = Set.of("PDF", "IMAGE");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ANALYZING", "READY", "APPLIED", "CLOSED");
    private static final int MOCK_JOB_ANALYSIS_CREDIT = 1;
    private static final int MOCK_FIT_ANALYSIS_CREDIT = 2;
    private static final int MOCK_DASHBOARD_SUMMARY_CREDIT = 1;
    private static final Set<String> LIST_VIEWS = Set.of("ACTIVE", "ARCHIVED", "DELETED");

    private final ApplicationCaseMapper applicationCaseMapper;
    private final ApplicationCaseExtractionMapper extractionMapper;
    private final ApplicationCaseAccessService accessService;
    private final JobPostingService jobPostingService;
    private final JobAnalysisService jobAnalysisService;
    private final CompanyAnalysisService companyAnalysisService;
    private final JobAnalysisMapper jobAnalysisMapper;
    private final OpenAiResponsesClient openAiClient;

    @Override
    @Transactional
    public ApplicationCaseResponse create(Long userId, CreateApplicationCaseRequest request) {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .userId(userId)
                .companyName(request.companyName().trim())
                .jobTitle(request.jobTitle().trim())
                .postingDate(request.postingDate())
                .deadlineDate(request.deadlineDate())
                .sourceType(normalizeOption(request.sourceType(), DEFAULT_SOURCE_TYPE, SOURCE_TYPES, "sourceType"))
                .status(normalizeOption(request.status(), DEFAULT_STATUS, STATUSES, "status"))
                .favorite(Boolean.TRUE.equals(request.favorite()))
                .build();
        applicationCaseMapper.insertApplicationCase(applicationCase);
        return ApplicationCaseResponse.from(accessService.requireOwned(userId, applicationCase.getId()));
    }

    @Override
    @Transactional
    public ApplicationCaseFromJobPostingResponse createFromJobPosting(Long userId,
                                                                      CreateApplicationCaseFromJobPostingRequest request) {
        PreparedJobPostingRequest prepared = prepareJobPostingRequest(request);
        JobPostingMetadataResponse metadata = safeDefaultMetadata();
        ApplicationCase applicationCase = insertApplicationCase(userId, metadata, prepared.sourceType(), request.favorite());
        JobPostingResponse jobPosting = jobPostingService.saveJobPostingForExtractionQueue(
                userId,
                applicationCase.getId(),
                prepared.request());
        ApplicationCaseExtractionResponse extractionJob = queueExtraction(
                userId,
                applicationCase.getId(),
                jobPosting.id(),
                prepared.sourceType());

        return new ApplicationCaseFromJobPostingResponse(
                ApplicationCaseResponse.from(accessService.requireOwned(userId, applicationCase.getId())),
                jobPosting,
                metadata,
                extractionJob);
    }

    @Override
    @Transactional
    public ApplicationCaseFromJobPostingResponse createFromJobPostingUpload(Long userId,
                                                                            MultipartFile file,
                                                                            String sourceType,
                                                                            Boolean favorite) {
        String normalizedSourceType = normalizeOption(sourceType, null, JOB_POSTING_UPLOAD_SOURCE_TYPES, "sourceType");
        JobPostingMetadataResponse metadata = safeDefaultMetadata();
        ApplicationCase applicationCase = insertApplicationCase(
                userId,
                metadata,
                normalizedSourceType,
                favorite);
        JobPostingResponse jobPosting = jobPostingService.saveUploadedJobPostingReferenceForNewCase(
                userId,
                applicationCase.getId(),
                file,
                normalizedSourceType);
        ApplicationCaseExtractionResponse extractionJob = queueExtraction(
                userId,
                applicationCase.getId(),
                jobPosting.id(),
                normalizedSourceType);

        return new ApplicationCaseFromJobPostingResponse(
                ApplicationCaseResponse.from(accessService.requireOwned(userId, applicationCase.getId())),
                jobPosting,
                metadata,
                extractionJob);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationCaseResponse> list(Long userId, String view, boolean includeArchived) {
        String normalizedView = normalizeListView(view);
        boolean includeArchivedForLegacyRequest = normalizedView == null && includeArchived;
        return applicationCaseMapper.findApplicationCasesByUserId(
                        userId,
                        normalizedView,
                        includeArchivedForLegacyRequest).stream()
                .map(ApplicationCaseResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationCaseResponse get(Long userId, Long id) {
        return ApplicationCaseResponse.from(accessService.requireOwned(userId, id));
    }

    @Override
    @Transactional
    public ApplicationCaseResponse update(Long userId, Long id, UpdateApplicationCaseRequest request) {
        ApplicationCase existing = accessService.requireOwned(userId, id);
        String nextStatus = normalizeOption(request.status(), existing.getStatus(), STATUSES, "status");
        LocalDateTime archivedAt = existing.getArchivedAt();
        if (Boolean.TRUE.equals(request.archived()) && archivedAt == null) {
            archivedAt = LocalDateTime.now();
        } else if (Boolean.FALSE.equals(request.archived())) {
            archivedAt = null;
        }
        ApplicationCase updated = ApplicationCase.builder()
                .id(existing.getId())
                .userId(userId)
                .companyName(defaultString(request.companyName(), existing.getCompanyName()))
                .jobTitle(defaultString(request.jobTitle(), existing.getJobTitle()))
                .postingDate(nextPostingDate(request, existing))
                .deadlineDate(nextDeadlineDate(request, existing))
                .sourceType(normalizeOption(request.sourceType(), existing.getSourceType(), SOURCE_TYPES, "sourceType"))
                .status(nextStatus)
                .favorite(request.favorite() != null ? request.favorite() : existing.isFavorite())
                .archivedAt(archivedAt)
                .build();
        applicationCaseMapper.updateApplicationCase(updated);
        if (!existing.getStatus().equals(nextStatus)) {
            applicationCaseMapper.insertStatusHistory(id, userId, existing.getStatus(), nextStatus, "USER_STATUS_UPDATE");
        }
        return ApplicationCaseResponse.from(accessService.requireOwned(userId, id));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        int deleted = applicationCaseMapper.softDeleteApplicationCase(id, userId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
        }
    }

    @Override
    @Transactional
    public void restore(Long userId, Long id) {
        int restored = applicationCaseMapper.restoreDeletedApplicationCase(id, userId);
        if (restored == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
        }
    }

    @Override
    @Transactional
    public JobPostingResponse saveJobPosting(Long userId, Long applicationCaseId, JobPostingRequest request) {
        String sourceType = normalizeOption(request.sourceType(), DEFAULT_SOURCE_TYPE, SOURCE_TYPES, "sourceType");
        if (needsBackgroundExtraction(sourceType, request)) {
            if ("URL".equals(sourceType)) {
                requiredText(request.uploadedFileUrl(), "uploadedFileUrl");
            } else if (JOB_POSTING_UPLOAD_SOURCE_TYPES.contains(sourceType)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "PDF/IMAGE 공고 추출은 파일 업로드 API를 사용해 주세요.");
            }
            JobPostingResponse jobPosting = jobPostingService.saveJobPostingForExtractionQueue(
                    userId,
                    applicationCaseId,
                    request);
            queueExtraction(userId, applicationCaseId, jobPosting.id(), sourceType);
            return jobPosting;
        }
        return jobPostingService.saveJobPosting(userId, applicationCaseId, request);
    }

    @Override
    @Transactional
    public JobPostingResponse uploadJobPostingFile(Long userId, Long applicationCaseId, MultipartFile file, String sourceType) {
        String normalizedSourceType = normalizeOption(sourceType, null, JOB_POSTING_UPLOAD_SOURCE_TYPES, "sourceType");
        JobPostingResponse jobPosting = jobPostingService.saveUploadedJobPostingReferenceForNewCase(
                userId,
                applicationCaseId,
                file,
                normalizedSourceType);
        queueExtraction(userId, applicationCaseId, jobPosting.id(), normalizedSourceType);
        return jobPosting;
    }

    @Override
    public JobPostingResponse getJobPosting(Long userId, Long applicationCaseId) {
        return jobPostingService.getJobPosting(userId, applicationCaseId);
    }

    @Override
    public List<JobPostingResponse> getJobPostingRevisions(Long userId, Long applicationCaseId) {
        return jobPostingService.getJobPostingRevisions(userId, applicationCaseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationCaseExtractionResponse> getActiveExtractions(Long userId) {
        return extractionMapper.findActiveExtractionsByUserId(userId).stream()
                .map(ApplicationCaseExtractionResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationCaseExtractionResponse getLatestJobPostingExtraction(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        ApplicationCaseExtraction extraction = extractionMapper.findLatestExtractionByApplicationCaseId(applicationCaseId);
        if (extraction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고 추출 작업을 찾을 수 없습니다.");
        }
        return ApplicationCaseExtractionResponse.from(extraction);
    }

    @Override
    @Transactional
    public ApplicationCaseExtractionResponse retryJobPostingExtraction(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        if (extractionMapper.countActiveExtractionsByApplicationCaseId(applicationCaseId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 진행 중인 공고 추출 작업이 있습니다.");
        }

        ApplicationCaseExtraction latestExtraction = extractionMapper.findLatestExtractionByApplicationCaseId(applicationCaseId);
        if (latestExtraction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고 추출 작업을 찾을 수 없습니다.");
        }
        if (!EXTRACTION_STATUS_FAILED.equals(latestExtraction.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "실패한 최신 공고 추출 작업만 재시도할 수 있습니다.");
        }

        Long failedJobPostingId = latestExtraction.getJobPostingId();
        if (failedJobPostingId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고문을 찾을 수 없습니다.");
        }
        jobPostingService.getJobPostingByIdForCase(userId, applicationCaseId, failedJobPostingId);
        return queueExtraction(
                userId,
                applicationCaseId,
                failedJobPostingId,
                latestExtraction.getSourceType());
    }

    @Override
    public JobAnalysisResponse createMockJobAnalysis(Long userId, Long applicationCaseId) {
        return jobAnalysisService.createMockJobAnalysis(userId, applicationCaseId);
    }

    @Override
    public JobAnalysisResponse createJobAnalysis(Long userId, Long applicationCaseId) {
        return jobAnalysisService.createJobAnalysis(userId, applicationCaseId);
    }

    @Override
    public JobAnalysisResponse getJobAnalysis(Long userId, Long applicationCaseId) {
        return jobAnalysisService.getJobAnalysis(userId, applicationCaseId);
    }

    @Override
    public List<JobAnalysisResponse> getJobAnalysisHistory(Long userId, Long applicationCaseId) {
        return jobAnalysisService.getJobAnalysisHistory(userId, applicationCaseId);
    }

    @Override
    public JobAnalysisResponse reviewJobAnalysis(Long userId, Long applicationCaseId, Long analysisId, JobAnalysisReviewRequest request) {
        return jobAnalysisService.reviewJobAnalysis(userId, applicationCaseId, analysisId, request);
    }

    @Override
    public CompanyAnalysisResponse createMockCompanyAnalysis(Long userId, Long applicationCaseId) {
        return companyAnalysisService.createMockCompanyAnalysis(userId, applicationCaseId);
    }

    @Override
    public CompanyAnalysisResponse createCompanyAnalysis(Long userId, Long applicationCaseId) {
        return companyAnalysisService.createCompanyAnalysis(userId, applicationCaseId);
    }

    @Override
    public CompanyAnalysisResponse getCompanyAnalysis(Long userId, Long applicationCaseId) {
        return companyAnalysisService.getCompanyAnalysis(userId, applicationCaseId);
    }

    @Override
    public List<CompanyAnalysisResponse> getCompanyAnalysisHistory(Long userId, Long applicationCaseId) {
        return companyAnalysisService.getCompanyAnalysisHistory(userId, applicationCaseId);
    }

    @Override
    public CompanyAnalysisResponse reviewCompanyAnalysis(Long userId, Long applicationCaseId, Long analysisId, CompanyAnalysisReviewRequest request) {
        return companyAnalysisService.reviewCompanyAnalysis(userId, applicationCaseId, analysisId, request);
    }

    @Override
    @Transactional
    public AnalysisResponse createMockAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = accessService.requireOwned(userId, applicationCaseId);
        String sourceText = accessService.sourceText(applicationCaseId);
        MockAnalysisSeed seed = MockAnalysisSeed.from(applicationCase, sourceText);

        JobAnalysis jobAnalysis = jobAnalysisService.createMockJobAnalysisEntity(applicationCase, sourceText);
        applicationCaseMapper.deleteFitAnalysesByCaseId(applicationCaseId);
        FitAnalysis fitAnalysis = FitAnalysis.builder()
                .applicationCaseId(applicationCaseId)
                .fitScore(seed.fitScore())
                .matchedSkills(seed.matchedSkills())
                .missingSkills(seed.missingSkills())
                .recommendedStudy(seed.recommendedStudy())
                .recommendedCertificates(seed.recommendedCertificates())
                .strategy(seed.strategy())
                .build();
        applicationCaseMapper.insertFitAnalysis(fitAnalysis);

        applicationCaseMapper.markAnalysisCompleted(applicationCaseId, userId);
        logMockAiUsage(userId, applicationCaseId, sourceText);

        return response(
                applicationCase,
                jobAnalysis,
                applicationCaseMapper.findLatestFitAnalysisByCaseId(applicationCaseId));
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = accessService.requireOwned(userId, applicationCaseId);
        return response(
                applicationCase,
                jobAnalysisMapper.findLatestJobAnalysisByCaseId(applicationCaseId),
                applicationCaseMapper.findLatestFitAnalysisByCaseId(applicationCaseId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiUsageFailureResponse> getAiUsageFailures(Long userId, Long applicationCaseId, int limit) {
        accessService.requireOwned(userId, applicationCaseId);
        return applicationCaseMapper.findBFailureLogsByCaseId(applicationCaseId, normalizeFailureLimit(limit));
    }

    private PreparedJobPostingRequest prepareJobPostingRequest(CreateApplicationCaseFromJobPostingRequest request) {
        String sourceType = normalizeOption(request.sourceType(), DEFAULT_SOURCE_TYPE, JOB_POSTING_JSON_SOURCE_TYPES, "sourceType");
        if ("URL".equals(sourceType)) {
            return prepareUrlJobPostingRequest(request);
        }
        String originalText = requiredText(request.originalText(), "originalText");
        return new PreparedJobPostingRequest(
                sourceType,
                new JobPostingRequest(originalText, null, null, sourceType));
    }

    private PreparedJobPostingRequest prepareUrlJobPostingRequest(CreateApplicationCaseFromJobPostingRequest request) {
        String uploadedFileUrl = requiredText(request.uploadedFileUrl(), "uploadedFileUrl");
        return new PreparedJobPostingRequest(
                "URL",
                new JobPostingRequest(null, uploadedFileUrl, blankToNull(request.extractedText()), "URL"));
    }

    private ApplicationCase insertApplicationCase(Long userId,
                                                  JobPostingMetadataResponse metadata,
                                                  String sourceType,
                                                  Boolean favorite) {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .userId(userId)
                .companyName(metadata.companyName())
                .jobTitle(metadata.jobTitle())
                .postingDate(metadata.postingDate())
                .deadlineDate(metadata.deadlineDate())
                .sourceType(sourceType)
                .status(DEFAULT_STATUS)
                .favorite(Boolean.TRUE.equals(favorite))
                .build();
        applicationCaseMapper.insertApplicationCase(applicationCase);
        return applicationCase;
    }

    private ApplicationCaseExtractionResponse queueExtraction(Long userId,
                                                              Long applicationCaseId,
                                                              Long jobPostingId,
                                                              String sourceType) {
        ApplicationCaseExtraction extraction = ApplicationCaseExtraction.builder()
                .applicationCaseId(applicationCaseId)
                .jobPostingId(jobPostingId)
                .userId(userId)
                .sourceType(sourceType)
                .status(EXTRACTION_STATUS_QUEUED)
                .build();
        try {
            extractionMapper.insertApplicationCaseExtraction(extraction);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 진행 중인 공고 추출 작업이 있습니다.");
        }
        return ApplicationCaseExtractionResponse.from(extraction);
    }

    private static boolean needsBackgroundExtraction(String sourceType, JobPostingRequest request) {
        if ("TEXT".equals(sourceType) || "MANUAL".equals(sourceType)) {
            return true;
        }
        return Set.of("URL", "PDF", "IMAGE").contains(sourceType) && isBlank(request.extractedText());
    }

    private static JobPostingMetadataResponse safeDefaultMetadata() {
        return new JobPostingMetadataResponse(DEFAULT_COMPANY_NAME, DEFAULT_JOB_TITLE, null, null);
    }

    private static String requiredText(String value, String fieldName) {
        if (isBlank(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "%s 값이 필요합니다.".formatted(fieldName));
        }
        return value.trim();
    }

    private static String sourceText(String primary, String fallback) {
        String text = !isBlank(primary) ? primary.trim() : blankToNull(fallback);
        if (isBlank(text)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공고 텍스트를 추출하지 못했습니다.");
        }
        return text;
    }

    private static AnalysisResponse response(ApplicationCase applicationCase, JobAnalysis jobAnalysis, FitAnalysis fitAnalysis) {
        return new AnalysisResponse(
                ApplicationCaseResponse.from(applicationCase),
                JobAnalysisResponse.from(jobAnalysis),
                FitAnalysisResponse.from(fitAnalysis));
    }

    private static String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static java.time.LocalDate nextDeadlineDate(UpdateApplicationCaseRequest request, ApplicationCase existing) {
        if (Boolean.TRUE.equals(request.clearDeadlineDate())) {
            return null;
        }
        return request.deadlineDate() != null ? request.deadlineDate() : existing.getDeadlineDate();
    }

    private static java.time.LocalDate nextPostingDate(UpdateApplicationCaseRequest request, ApplicationCase existing) {
        if (Boolean.TRUE.equals(request.clearPostingDate())) {
            return null;
        }
        return request.postingDate() != null ? request.postingDate() : existing.getPostingDate();
    }

    private void logMockAiUsage(Long userId, Long applicationCaseId, String sourceText) {
        int baseTokens = estimateTokenUsage(sourceText);
        insertMockAiUsage(userId, applicationCaseId, "JOB_ANALYSIS", baseTokens, MOCK_JOB_ANALYSIS_CREDIT);
        insertMockAiUsage(userId, applicationCaseId, "FIT_ANALYSIS", baseTokens + 620, MOCK_FIT_ANALYSIS_CREDIT);
        insertMockAiUsage(userId, applicationCaseId, "DASHBOARD_SUMMARY", 980, MOCK_DASHBOARD_SUMMARY_CREDIT);
    }

    private void insertMockAiUsage(Long userId, Long applicationCaseId, String featureType, int tokenUsage, int creditUsed) {
        applicationCaseMapper.insertAiUsageLog(AiUsageLog.builder()
                .userId(userId)
                .applicationCaseId(applicationCaseId)
                .featureType(featureType)
                .status("SUCCESS")
                .model("mock")
                .tokenUsage(tokenUsage)
                .creditUsed(creditUsed)
                .build());
    }

    private static int estimateTokenUsage(String sourceText) {
        int length = sourceText == null ? 0 : sourceText.length();
        return Math.max(1200, Math.min(4200, 900 + length * 2));
    }

    private static int normalizeFailureLimit(int limit) {
        if (limit <= 0) {
            return 5;
        }
        return Math.min(limit, 50);
    }

    private static String normalizeListView(String view) {
        if (isBlank(view)) {
            return null;
        }
        String normalized = view.trim().toUpperCase(Locale.ROOT);
        if (!LIST_VIEWS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "view 값이 올바르지 않습니다.");
        }
        return normalized;
    }

    private static String normalizeOption(String value, String defaultValue, Set<String> allowedValues, String fieldName) {
        String normalized = isBlank(value) ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !allowedValues.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "%s 값이 올바르지 않습니다.".formatted(fieldName));
        }
        return normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record PreparedJobPostingRequest(
            String sourceType,
            JobPostingRequest request
    ) {
    }
}
