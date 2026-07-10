package com.careertuner.jobposting.service;

import java.util.Locale;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.applicationcase.support.BDisplayTime;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.dto.JobPostingRequest;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.mapper.JobPostingMapper;
import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobPostingService {

    private static final String DEFAULT_SOURCE_TYPE = "TEXT";
    private static final String FEATURE_JOB_POSTING_OCR = "JOB_POSTING_OCR";
    private static final int MAX_REVISION_INSERT_ATTEMPTS = 3;
    private static final Set<String> SOURCE_TYPES = Set.of("TEXT", "PDF", "IMAGE", "URL", "MANUAL");

    private final ApplicationCaseAccessService accessService;
    private final JobPostingMapper jobPostingMapper;
    private final AiUsageLogService aiUsageLogService;
    private final JobPostingFileStorage fileStorage;
    private final JobPostingTextExtractor textExtractor;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobPostingResponse saveJobPosting(Long userId, Long applicationCaseId, JobPostingRequest request) {
        accessService.requireOwned(userId, applicationCaseId);
        String sourceType = normalizeOption(request.sourceType(), DEFAULT_SOURCE_TYPE, SOURCE_TYPES, "sourceType");
        if ("URL".equals(sourceType)) {
            if (isBlank(request.uploadedFileUrl())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "공고 URL이 필요합니다.");
            }
            if (isBlank(request.extractedText())) {
                ExtractedPosting extracted = textExtractor.extractUrl(request.uploadedFileUrl());
                return saveExtractedPosting(applicationCaseId, extracted);
            }
        }

        validateJobPosting(request);
        JobPosting jobPosting = JobPosting.builder()
                .applicationCaseId(applicationCaseId)
                .originalText("URL".equals(sourceType) ? null : blankToNull(request.originalText()))
                .uploadedFileUrl(blankToNull(request.uploadedFileUrl()))
                .extractedText(blankToNull(request.extractedText()))
                .sourceType(sourceType)
                .build();
        return replaceJobPosting(applicationCaseId, jobPosting);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobPostingResponse saveJobPostingForExtractionQueue(Long userId, Long applicationCaseId, JobPostingRequest request) {
        accessService.requireOwned(userId, applicationCaseId);
        String sourceType = normalizeOption(request.sourceType(), DEFAULT_SOURCE_TYPE, SOURCE_TYPES, "sourceType");
        validateJobPosting(request);
        JobPosting jobPosting = JobPosting.builder()
                .applicationCaseId(applicationCaseId)
                .originalText(blankToNull(request.originalText()))
                .uploadedFileUrl(blankToNull(request.uploadedFileUrl()))
                .extractedText(blankToNull(request.extractedText()))
                .sourceType(sourceType)
                .build();
        return replaceJobPosting(applicationCaseId, jobPosting);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobPostingResponse uploadJobPostingFile(Long userId, Long applicationCaseId, MultipartFile file, String sourceType) {
        return uploadJobPostingFile(userId, applicationCaseId, file, sourceType, applicationCaseId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobPostingResponse uploadJobPostingFileForNewCase(Long userId, Long applicationCaseId, MultipartFile file, String sourceType) {
        return uploadJobPostingFile(userId, applicationCaseId, file, sourceType, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobPostingResponse saveUploadedJobPostingReferenceForNewCase(Long userId,
                                                                        Long applicationCaseId,
                                                                        MultipartFile file,
                                                                        String sourceType) {
        accessService.requireOwned(userId, applicationCaseId);
        String normalizedSourceType = normalizeOption(sourceType, null, SOURCE_TYPES, "sourceType");
        StoredJobPostingFile storedFile = fileStorage.store(applicationCaseId, file, normalizedSourceType);
        JobPosting jobPosting = JobPosting.builder()
                .applicationCaseId(applicationCaseId)
                .uploadedFileUrl(storedFile.fileReference())
                .sourceType(storedFile.sourceType())
                .build();
        return replaceJobPosting(applicationCaseId, jobPosting);
    }

    private JobPostingResponse uploadJobPostingFile(Long userId,
                                                    Long applicationCaseId,
                                                    MultipartFile file,
                                                    String sourceType,
                                                    Long failureLogApplicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        try {
            StoredJobPostingFile storedFile = fileStorage.store(applicationCaseId, file, sourceType);
            ExtractedPosting extracted = textExtractor.extractFile(storedFile);
            if (extracted.usage() != null) {
                aiUsageLogService.recordSuccess(userId, applicationCaseId, FEATURE_JOB_POSTING_OCR, extracted.usage());
            }
            return saveExtractedPosting(applicationCaseId, extracted);
        } catch (RuntimeException ex) {
            aiUsageLogService.recordFailure(userId, failureLogApplicationCaseId, FEATURE_JOB_POSTING_OCR, ex.getMessage());
            throw ex;
        }
    }

    public ExtractedPosting extractUrlJobPosting(String uploadedFileUrl) {
        return textExtractor.extractUrl(uploadedFileUrl);
    }

    public ExtractedPosting extractUploadedJobPosting(Long userId,
                                                       Long applicationCaseId,
                                                       String sourceType,
                                                       String uploadedFileUrl,
                                                       String ocrRequestedProvider) {
        accessService.requireOwned(userId, applicationCaseId);
        try {
            StoredJobPostingFile storedFile = fileStorage.load(applicationCaseId, uploadedFileUrl, sourceType);
            // 등록 시 고른 OCR provider 를 primary 로 라우팅(미선택=null=기본 자동 체인).
            ExtractedPosting extracted = textExtractor.extractFile(storedFile, ocrRequestedProvider);
            if (extracted.usage() != null) {
                aiUsageLogService.recordSuccess(userId, applicationCaseId, FEATURE_JOB_POSTING_OCR, extracted.usage());
            }
            return extracted;
        } catch (RuntimeException ex) {
            aiUsageLogService.recordFailure(userId, applicationCaseId, FEATURE_JOB_POSTING_OCR, ex.getMessage());
            throw ex;
        }
    }

    /**
     * 동기 strict 재추출용 OCR — 사용자가 고른 provider 하나만 호출한다(교차 provider 폴백 없음).
     * <b>트랜잭션을 열지 않는다</b>(OCR 원격 호출이 짧은 저장 트랜잭션 밖에서 돌도록 호출부가 경계를 분리).
     * provider 실패·빈 결과는 예외가 아니라 FAILED 마커 {@link ExtractedPosting}(qualityStatus=FAILED)으로 온다.
     */
    public ExtractedPosting extractUploadedJobPostingStrict(Long userId,
                                                            Long applicationCaseId,
                                                            String sourceType,
                                                            String uploadedFileUrl,
                                                            String ocrRequestedProvider) {
        accessService.requireOwned(userId, applicationCaseId);
        try {
            StoredJobPostingFile storedFile = fileStorage.load(applicationCaseId, uploadedFileUrl, sourceType);
            ExtractedPosting extracted = textExtractor.extractFileStrict(storedFile, ocrRequestedProvider);
            if (extracted.usage() != null) {
                aiUsageLogService.recordSuccess(userId, applicationCaseId, FEATURE_JOB_POSTING_OCR, extracted.usage());
            }
            return extracted;
        } catch (RuntimeException ex) {
            aiUsageLogService.recordFailure(userId, applicationCaseId, FEATURE_JOB_POSTING_OCR, ex.getMessage());
            throw ex;
        }
    }

    /** 재추출 평가에 필요한 원본 공고 도메인 조회(소유권 확인 포함). 응답 DTO 가 아닌 도메인을 반환한다. */
    @Transactional(readOnly = true)
    public JobPosting getJobPostingDomainForCase(Long userId, Long applicationCaseId, Long jobPostingId) {
        accessService.requireOwned(userId, applicationCaseId);
        JobPosting jobPosting = jobPostingMapper.findJobPostingByIdAndCaseId(jobPostingId, applicationCaseId);
        if (jobPosting == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고문을 찾을 수 없습니다.");
        }
        return jobPosting;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobPostingResponse saveExtractedJobPosting(Long userId, Long applicationCaseId, ExtractedPosting extracted) {
        accessService.requireOwned(userId, applicationCaseId);
        return saveExtractedPosting(applicationCaseId, extracted);
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getJobPosting(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        JobPosting jobPosting = jobPostingMapper.findLatestJobPostingByCaseId(applicationCaseId);
        if (jobPosting == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고문을 찾을 수 없습니다.");
        }
        return toResponse(jobPosting);
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getJobPostingByIdForCase(Long userId, Long applicationCaseId, Long jobPostingId) {
        accessService.requireOwned(userId, applicationCaseId);
        JobPosting jobPosting = jobPostingMapper.findJobPostingByIdAndCaseId(jobPostingId, applicationCaseId);
        if (jobPosting == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고문을 찾을 수 없습니다.");
        }
        return toResponse(jobPosting);
    }

    @Transactional(readOnly = true)
    public List<JobPostingResponse> getJobPostingRevisions(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        return jobPostingMapper.findJobPostingRevisionsByCaseId(applicationCaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** created_at 은 DB CURRENT_TIMESTAMP(UTC)로 저장된다. 화면(KST) 표시를 위해 응답 직전 UTC→KST 로 보정한다. */
    private JobPostingResponse toResponse(JobPosting jobPosting) {
        if (jobPosting == null) {
            return null;
        }
        jobPosting.setCreatedAt(BDisplayTime.dbToDisplay(jobPosting.getCreatedAt()));
        return JobPostingResponse.from(jobPosting);
    }

    private JobPostingResponse saveExtractedPosting(Long applicationCaseId, ExtractedPosting extracted) {
        JobPosting jobPosting = JobPosting.builder()
                .applicationCaseId(applicationCaseId)
                .originalText(blankToNull(extracted.originalText()))
                .uploadedFileUrl(blankToNull(extracted.uploadedFileUrl()))
                .extractedText(blankToNull(extracted.extractedText()))
                .sourceType(extracted.sourceType())
                .build();
        return replaceJobPosting(applicationCaseId, jobPosting);
    }

    private JobPostingResponse replaceJobPosting(Long applicationCaseId, JobPosting jobPosting) {
        for (int attempt = 0; attempt < MAX_REVISION_INSERT_ATTEMPTS; attempt++) {
            jobPosting.setId(null);
            jobPosting.setRevision(jobPostingMapper.nextRevisionForCase(applicationCaseId));
            try {
                jobPostingMapper.insertJobPosting(jobPosting);
                JobPosting inserted = jobPostingMapper.findJobPostingByIdAndCaseId(jobPosting.getId(), applicationCaseId);
                if (inserted == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "저장된 공고문을 찾을 수 없습니다.");
                }
                return toResponse(inserted);
            } catch (DuplicateKeyException ex) {
                if (attempt == MAX_REVISION_INSERT_ATTEMPTS - 1) {
                    throw new BusinessException(ErrorCode.CONFLICT, "공고문 버전 충돌이 반복되었습니다. 다시 시도해 주세요.");
                }
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT, "공고문 버전 충돌이 반복되었습니다. 다시 시도해 주세요.");
    }

    private static void validateJobPosting(JobPostingRequest request) {
        if (isBlank(request.originalText()) && isBlank(request.extractedText()) && isBlank(request.uploadedFileUrl())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공고 원문, 추출 텍스트, 파일 참조 중 하나는 필요합니다.");
        }
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String normalizeOption(String value, String defaultValue, Set<String> allowedValues, String fieldName) {
        String normalized = isBlank(value) ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "%s 값이 올바르지 않습니다.".formatted(fieldName));
        }
        return normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
