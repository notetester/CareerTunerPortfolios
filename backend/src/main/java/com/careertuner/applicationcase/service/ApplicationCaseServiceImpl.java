package com.careertuner.applicationcase.service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;
import com.careertuner.applicationcase.domain.ApplicationCaseInitialRun;
import com.careertuner.applicationcase.domain.FitAnalysis;
import com.careertuner.applicationcase.dto.AnalysisResponse;
import com.careertuner.applicationcase.dto.AiUsageFailureResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseExtractionResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseFromJobPostingResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.dto.CreateApplicationCaseFromJobPostingRequest;
import com.careertuner.applicationcase.dto.CreateApplicationCaseRequest;
import com.careertuner.applicationcase.support.BDisplayTime;
import com.careertuner.applicationcase.dto.FitAnalysisResponse;
import com.careertuner.applicationcase.dto.JobPostingMetadataResponse;
import com.careertuner.applicationcase.dto.ConfirmJobPostingExtractionRequest;
import com.careertuner.applicationcase.dto.ReviewJobPostingExtractionRequest;
import com.careertuner.applicationcase.dto.UpdateApplicationCaseRequest;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseInitialRunMapper;
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
import com.careertuner.jobposting.dto.JobPostingRequest;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.notification.mapper.NotificationMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApplicationCaseServiceImpl implements ApplicationCaseService {

    private static final String DEFAULT_SOURCE_TYPE = "TEXT";
    private static final String DEFAULT_STATUS = "DRAFT";
    private static final String DEFAULT_COMPANY_NAME = "\uAE30\uC5C5\uBA85 \uD655\uC778 \uD544\uC694";
    private static final String DEFAULT_JOB_TITLE = "\uC9C1\uBB34\uBA85 \uD655\uC778 \uD544\uC694";
    private static final String EXTRACTION_STATUS_QUEUED = "QUEUED";
    private static final String EXTRACTION_STATUS_FAILED = "FAILED";
    private static final String EXTRACTION_STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String EXTRACTION_QUALITY_PASS = "PASS";
    private static final String EXTRACTION_QUALITY_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    private static final String NOTIFICATION_TARGET_TYPE = "APPLICATION_CASE";
    private static final String REVIEW_NOTIFICATION_TYPE = "JOB_POSTING_EXTRACTION_REVIEW_REQUIRED";
    private static final int REVIEWED_QUALITY_SCORE = 100;
    private static final Set<String> SOURCE_TYPES = Set.of("TEXT", "PDF", "IMAGE", "URL", "MANUAL");
    private static final Set<String> JOB_POSTING_JSON_SOURCE_TYPES = Set.of("TEXT", "MANUAL", "URL");
    private static final Set<String> JOB_POSTING_UPLOAD_SOURCE_TYPES = Set.of("PDF", "IMAGE");
    private static final Set<String> ANALYSIS_PROVIDERS = Set.of("LOCAL", "CLAUDE", "OPENAI");
    private static final Set<String> OCR_PROVIDERS = Set.of("CLAUDE", "OPENAI", "SELF_OCR");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ANALYZING", "READY", "APPLIED", "CLOSED");
    private static final int MAX_EXTRACTION_LOOKUP_CASE_IDS = 200;
    private static final Set<String> LIST_VIEWS = Set.of("ACTIVE", "ARCHIVED", "DELETED");

    private final ApplicationCaseMapper applicationCaseMapper;
    private final ApplicationCaseExtractionMapper extractionMapper;
    private final ApplicationCaseAccessService accessService;
    private final JobPostingService jobPostingService;
    private final JobAnalysisService jobAnalysisService;
    private final CompanyAnalysisService companyAnalysisService;
    private final JobAnalysisMapper jobAnalysisMapper;
    private final OpenAiResponsesClient openAiClient;
    private final NotificationMapper notificationMapper;
    private final ApplicationCaseAutoPipelineService autoPipelineService;
    private final ApplicationCaseInitialRunMapper initialRunMapper;

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
        return toResponse(accessService.requireOwned(userId, applicationCase.getId()));
    }

    @Override
    @Transactional
    public ApplicationCaseFromJobPostingResponse createFromJobPosting(Long userId,
                                                                      CreateApplicationCaseFromJobPostingRequest request) {
        // 부수효과(케이스·공고·추출 큐 생성) 전에 선택값을 먼저 검증·정규화 → 잘못된 요청은 아무 행도 만들지 않고 400.
        String jobProvider = validateProvider(request.jobAnalysisProvider(), "jobAnalysisProvider");
        String companyProvider = validateProvider(request.companyAnalysisProvider(), "companyAnalysisProvider");
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
        // 초기 실행 프로필(PENDING) — 등록 시 고른 공고/기업 분석 provider(검증·정규화됨)를 저장(async 파이프라인이 읽음).
        createInitialRunProfile(applicationCase.getId(), jobProvider, companyProvider);

        return new ApplicationCaseFromJobPostingResponse(
                toResponse(accessService.requireOwned(userId, applicationCase.getId())),
                jobPosting,
                metadata,
                extractionJob);
    }

    @Override
    @Transactional
    public ApplicationCaseFromJobPostingResponse createFromJobPostingUpload(Long userId,
                                                                            MultipartFile file,
                                                                            String sourceType,
                                                                            Boolean favorite,
                                                                            String jobAnalysisProvider,
                                                                            String companyAnalysisProvider,
                                                                            String ocrProvider) {
        // 부수효과(파일 저장·케이스·추출 큐) 전에 선택값을 먼저 검증·정규화 → 잘못된 요청은 파일도 저장하지 않고 400.
        String jobProvider = validateProvider(jobAnalysisProvider, "jobAnalysisProvider");
        String companyProvider = validateProvider(companyAnalysisProvider, "companyAnalysisProvider");
        String ocrRequestedProvider = validateOcrProvider(ocrProvider);
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
        // 선택한 OCR provider 를 추출 작업에 스냅샷 → 워커가 이 값으로 primary OCR 을 라우팅한다(미선택=기본 자동 체인).
        ApplicationCaseExtractionResponse extractionJob = queueExtraction(
                userId,
                applicationCase.getId(),
                jobPosting.id(),
                normalizedSourceType,
                ocrRequestedProvider);
        // 초기 실행 프로필(PENDING). 분석 provider(검증·정규화됨) 저장.
        createInitialRunProfile(applicationCase.getId(), jobProvider, companyProvider);

        return new ApplicationCaseFromJobPostingResponse(
                toResponse(accessService.requireOwned(userId, applicationCase.getId())),
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
                .map(ApplicationCaseServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationCaseResponse get(Long userId, Long id) {
        return toResponse(accessService.requireOwned(userId, id));
    }

    @Override
    @Transactional
    public ApplicationCaseResponse update(Long userId, Long id, UpdateApplicationCaseRequest request) {
        ApplicationCase existing = accessService.requireOwned(userId, id);
        String nextStatus = normalizeOption(request.status(), existing.getStatus(), STATUSES, "status");
        LocalDateTime archivedAt = existing.getArchivedAt();
        if (Boolean.TRUE.equals(request.archived()) && archivedAt == null) {
            // archived_at 은 Java 가 찍는 시각 → JVM tz 무관하게 KST 로(BDisplayTime).
            archivedAt = BDisplayTime.now();
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
        return toResponse(accessService.requireOwned(userId, id));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        int deleted = applicationCaseMapper.softDeleteApplicationCase(id, userId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
        }
    }

    /** 초기 실행 프로필(PENDING)을 만든다. provider 는 이미 등록 메서드 시작부에서 검증·정규화된 값이다. */
    private void createInitialRunProfile(Long applicationCaseId, String jobAnalysisProvider, String companyAnalysisProvider) {
        initialRunMapper.insertPending(ApplicationCaseInitialRun.builder()
                .applicationCaseId(applicationCaseId)
                .jobAnalysisProvider(jobAnalysisProvider)
                .companyAnalysisProvider(companyAnalysisProvider)
                .build());
    }

    /**
     * 초기 자동 파이프라인이 아직 진행 중(프로필 PENDING/RUNNING)이면 사용자의 수동 재분석을 CONFLICT 로 거절한다.
     * 초기 실행과 수동 실행이 같은 지원 건에서 동시에 분석 행을 만들지 않도록 막는 가드다. 프로필이 없거나
     * 이미 DONE/FAILED 면 통과시켜 기존 재분석 경로를 그대로 둔다.
     */
    private void guardInitialRunNotInProgress(Long applicationCaseId) {
        ApplicationCaseInitialRun run = initialRunMapper.findByApplicationCaseId(applicationCaseId);
        if (run != null && ("PENDING".equals(run.getState()) || "RUNNING".equals(run.getState()))) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "초기 분석이 아직 진행 중입니다. 완료된 후 다시 시도해 주세요.");
        }
    }

    /**
     * 분석 provider 선택값 검증·정규화. 생략(null/공백)이면 null(현행 기본 체인). 비어 있지 않으면 대문자 정규화하고,
     * LOCAL/CLAUDE/OPENAI 가 아니면 사용자의 명시 선택이 유효하지 않으므로 400(INVALID_INPUT)으로 거절한다
     * — 조용히 무시하지 않는다(서버 재검증 규칙 일치).
     */
    private static String validateProvider(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ANALYSIS_PROVIDERS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "지원하지 않는 분석 모델입니다: %s=%s".formatted(field, value));
        }
        return normalized;
    }

    /**
     * OCR provider 선택값 검증·정규화. 생략(null/공백)이면 null(기본 자동 체인: Claude→OpenAI→워커).
     * 비어 있지 않으면 대문자 정규화하고 CLAUDE/OPENAI/SELF_OCR 가 아니면 400 으로 거절한다
     * (분석 provider 와 후보 집합이 달라 별도 검증 — LOCAL 은 OCR 대상 아님).
     */
    private static String validateOcrProvider(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!OCR_PROVIDERS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "지원하지 않는 OCR 모델입니다: ocrProvider=%s".formatted(value));
        }
        return normalized;
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
    public void hideFromTrash(Long userId, Long id) {
        int hidden = applicationCaseMapper.hideApplicationCaseFromTrash(id, userId);
        if (hidden == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "삭제함에서 지원 건을 찾을 수 없습니다.");
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
            syncApplicationCaseSourceType(userId, applicationCaseId, jobPosting.sourceType());
            queueExtraction(userId, applicationCaseId, jobPosting.id(), sourceType);
            return jobPosting;
        }
        JobPostingResponse jobPosting = jobPostingService.saveJobPosting(userId, applicationCaseId, request);
        syncApplicationCaseSourceType(userId, applicationCaseId, jobPosting.sourceType());
        return jobPosting;
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
        syncApplicationCaseSourceType(userId, applicationCaseId, jobPosting.sourceType());
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
    @Transactional(readOnly = true)
    public List<ApplicationCaseExtractionResponse> getLatestJobPostingExtractions(Long userId, List<Long> applicationCaseIds) {
        List<Long> normalizedIds = normalizeApplicationCaseIds(applicationCaseIds);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        return extractionMapper.findLatestExtractionsByApplicationCaseIdsAndUserId(userId, normalizedIds).stream()
                .map(ApplicationCaseExtractionResponse::from)
                .toList();
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "Job posting extraction job was not found.");
        }
        if (!EXTRACTION_STATUS_FAILED.equals(latestExtraction.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "실패한 최신 공고 추출 작업만 재시도할 수 있습니다.");
        }

        Long failedJobPostingId = latestExtraction.getJobPostingId();
        if (failedJobPostingId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고문을 찾을 수 없습니다.");
        }
        jobPostingService.getJobPostingByIdForCase(userId, applicationCaseId, failedJobPostingId);
        // 추출 실패 종결 시 FAILED 로 닫힌 초기 실행 프로필을 PENDING 으로 되살려,
        // 재추출 성공 시 초기 파이프라인이 다시 claim 해 1회 실행되게 한다(프로필 없거나 FAILED 아니면 0행).
        initialRunMapper.reopenForRetry(applicationCaseId);
        // 최초 등록 때 고른 OCR provider 를 재시도에도 이어받는다(사용자가 다시 고르지 않아도 같은 primary 로 라우팅).
        return queueExtraction(
                userId,
                applicationCaseId,
                failedJobPostingId,
                latestExtraction.getSourceType(),
                latestExtraction.getOcrRequestedProvider());
    }

    @Override
    @Transactional
    public ApplicationCaseExtractionResponse reviewJobPostingExtraction(Long userId,
                                                                        Long applicationCaseId,
                                                                        ReviewJobPostingExtractionRequest request) {
        accessService.requireOwned(userId, applicationCaseId);
        ApplicationCaseExtraction latestExtraction = extractionMapper.findLatestExtractionByApplicationCaseId(applicationCaseId);
        if (latestExtraction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Job posting extraction job was not found.");
        }
        if (!EXTRACTION_STATUS_SUCCEEDED.equals(latestExtraction.getStatus())
                || !EXTRACTION_QUALITY_REVIEW_REQUIRED.equals(latestExtraction.getQualityStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only REVIEW_REQUIRED extraction jobs can be reviewed.");
        }

        return applyConfirmedPosting(userId, applicationCaseId, latestExtraction, request.extractedText());
    }

    @Override
    @Transactional
    public ApplicationCaseExtractionResponse confirmEditedPosting(Long userId,
                                                                  Long applicationCaseId,
                                                                  ConfirmJobPostingExtractionRequest request) {
        accessService.requireOwned(userId, applicationCaseId);
        ApplicationCaseExtraction latestExtraction = extractionMapper.findLatestExtractionByApplicationCaseId(applicationCaseId);
        if (latestExtraction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Job posting extraction job was not found.");
        }
        // 사용자가 직접 고친 텍스트는 OCR/추출 품질 게이트를 다시 탈 대상이 아니라 검수된 입력으로 본다.
        // 추출이 끝난(PASS 또는 REVIEW_REQUIRED) 건만 확정 가능. 진행 중/실패 건은 거부한다.
        if (!EXTRACTION_STATUS_SUCCEEDED.equals(latestExtraction.getStatus())
                || !(EXTRACTION_QUALITY_PASS.equals(latestExtraction.getQualityStatus())
                        || EXTRACTION_QUALITY_REVIEW_REQUIRED.equals(latestExtraction.getQualityStatus()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only a completed extraction can be confirmed.");
        }

        return applyConfirmedPosting(userId, applicationCaseId, latestExtraction, request.extractedText());
    }

    /**
     * 사용자가 확정한 텍스트를 검수된 최신 공고문(MANUAL revision)으로 저장하고, OCR/추출을 다시 돌리지 않고
     * 분석 파이프라인만 1회 실행한다. 검수(review)와 수정 확정(confirm)이 공유하는 내부 로직이다.
     */
    private ApplicationCaseExtractionResponse applyConfirmedPosting(Long userId,
                                                                    Long applicationCaseId,
                                                                    ApplicationCaseExtraction latestExtraction,
                                                                    String requestedText) {
        String reviewedText = requiredText(requestedText, "extractedText");
        JobPostingResponse reviewedPosting = jobPostingService.saveJobPosting(
                userId,
                applicationCaseId,
                new JobPostingRequest(reviewedText, null, reviewedText, "MANUAL"));
        int updated = extractionMapper.markExtractionReviewed(
                latestExtraction.getId(),
                reviewedPosting.id(),
                REVIEWED_QUALITY_SCORE,
                reviewedQualityReportJson(reviewedText.length()),
                reviewedModelVersionsJson());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "Extraction review could not be completed. Please refresh and try again.");
        }

        notificationMapper.markTypeAsReadByTarget(
                userId,
                REVIEW_NOTIFICATION_TYPE,
                NOTIFICATION_TARGET_TYPE,
                applicationCaseId);

        autoPipelineService.runAfterExtractionPass(
                userId,
                applicationCaseId,
                reviewedPosting.id(),
                reviewedPosting.revision(),
                reviewedText);

        return ApplicationCaseExtractionResponse.from(
                extractionMapper.findLatestExtractionByApplicationCaseId(applicationCaseId));
    }

    @Override
    public JobAnalysisResponse createJobAnalysis(Long userId, Long applicationCaseId) {
        guardInitialRunNotInProgress(applicationCaseId);
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
    public CompanyAnalysisResponse createCompanyAnalysis(Long userId, Long applicationCaseId) {
        guardInitialRunNotInProgress(applicationCaseId);
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
        // created_at 은 DB CURRENT_TIMESTAMP(UTC)로 저장된다. 화면(KST) 표시를 위해 UTC→KST 로 보정한다(BDisplayTime).
        return applicationCaseMapper.findBFailureLogsByCaseId(applicationCaseId, normalizeFailureLimit(limit)).stream()
                .map(failure -> new AiUsageFailureResponse(
                        failure.featureType(),
                        failure.errorMessage(),
                        BDisplayTime.dbToDisplay(failure.createdAt())))
                .toList();
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

    /** OCR provider 선택이 없는 경로(TEXT/URL/MANUAL 등)용 — 기본 자동 체인으로 추출한다. */
    private ApplicationCaseExtractionResponse queueExtraction(Long userId,
                                                              Long applicationCaseId,
                                                              Long jobPostingId,
                                                              String sourceType) {
        return queueExtraction(userId, applicationCaseId, jobPostingId, sourceType, null);
    }

    private ApplicationCaseExtractionResponse queueExtraction(Long userId,
                                                              Long applicationCaseId,
                                                              Long jobPostingId,
                                                              String sourceType,
                                                              String ocrRequestedProvider) {
        ApplicationCaseExtraction extraction = ApplicationCaseExtraction.builder()
                .applicationCaseId(applicationCaseId)
                .jobPostingId(jobPostingId)
                .userId(userId)
                .sourceType(sourceType)
                .ocrRequestedProvider(ocrRequestedProvider)
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

    private void syncApplicationCaseSourceType(Long userId, Long applicationCaseId, String sourceType) {
        String normalizedSourceType = normalizeOption(sourceType, DEFAULT_SOURCE_TYPE, SOURCE_TYPES, "sourceType");
        applicationCaseMapper.updateApplicationCaseSourceType(applicationCaseId, userId, normalizedSourceType);
    }

    private static JobPostingMetadataResponse safeDefaultMetadata() {
        return new JobPostingMetadataResponse(DEFAULT_COMPANY_NAME, DEFAULT_JOB_TITLE, null, null);
    }

    private static String reviewedQualityReportJson(int textLength) {
        return "{\"qualityStatus\":\"PASS\",\"reviewed\":true,\"reviewedTextLength\":%d}".formatted(textLength);
    }

    private static String reviewedModelVersionsJson() {
        return "{\"review\":\"user-confirmed-v1\"}";
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
        if (jobAnalysis != null) {
            // created_at 은 DB CURRENT_TIMESTAMP(UTC)로 저장된다. B 응답과 동일하게 KST 로 맞춘다(BDisplayTime).
            jobAnalysis.setCreatedAt(BDisplayTime.dbToDisplay(jobAnalysis.getCreatedAt()));
        }
        return new AnalysisResponse(
                toResponse(applicationCase),
                JobAnalysisResponse.from(jobAnalysis),
                FitAnalysisResponse.from(fitAnalysis));
    }

    /**
     * 지원 건 응답 시각을 KST 로 맞춘다. {@code created_at}/{@code updated_at}/{@code deleted_at} 은 DB(UTC)로
     * 저장되므로 UTC→KST 변환하고, {@code archived_at} 은 Java(now)로 이미 KST 라 그대로 둔다(BDisplayTime).
     */
    private static ApplicationCaseResponse toResponse(ApplicationCase applicationCase) {
        if (applicationCase == null) {
            return null;
        }
        applicationCase.setCreatedAt(BDisplayTime.dbToDisplay(applicationCase.getCreatedAt()));
        applicationCase.setUpdatedAt(BDisplayTime.dbToDisplay(applicationCase.getUpdatedAt()));
        applicationCase.setDeletedAt(BDisplayTime.dbToDisplay(applicationCase.getDeletedAt()));
        return ApplicationCaseResponse.from(applicationCase);
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

    private static int normalizeFailureLimit(int limit) {
        if (limit <= 0) {
            return 5;
        }
        return Math.min(limit, 50);
    }

    private static List<Long> normalizeApplicationCaseIds(List<Long> applicationCaseIds) {
        if (applicationCaseIds == null || applicationCaseIds.isEmpty()) {
            return List.of();
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : applicationCaseIds) {
            if (id == null || id <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "applicationCaseIds 값이 올바르지 않습니다.");
            }
            uniqueIds.add(id);
        }
        if (uniqueIds.size() > MAX_EXTRACTION_LOOKUP_CASE_IDS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "applicationCaseIds는 200개까지 조회할 수 있습니다.");
        }
        return List.copyOf(uniqueIds);
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
