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
    public JobPostingResponse uploadJobPostingFile(Long userId, Long applicationCaseId, MultipartFile file, String sourceType) {
        accessService.requireOwned(userId, applicationCaseId);
        try {
            StoredJobPostingFile storedFile = fileStorage.store(applicationCaseId, file, sourceType);
            ExtractedPosting extracted = textExtractor.extractFile(storedFile);
            if (extracted.usage() != null) {
                aiUsageLogService.recordSuccess(userId, applicationCaseId, FEATURE_JOB_POSTING_OCR, extracted.usage());
            }
            return saveExtractedPosting(applicationCaseId, extracted);
        } catch (RuntimeException ex) {
            aiUsageLogService.recordFailure(userId, applicationCaseId, FEATURE_JOB_POSTING_OCR, ex.getMessage());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getJobPosting(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        JobPosting jobPosting = jobPostingMapper.findLatestJobPostingByCaseId(applicationCaseId);
        if (jobPosting == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고문을 찾을 수 없습니다.");
        }
        return JobPostingResponse.from(jobPosting);
    }

    @Transactional(readOnly = true)
    public List<JobPostingResponse> getJobPostingRevisions(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        return jobPostingMapper.findJobPostingRevisionsByCaseId(applicationCaseId).stream()
                .map(JobPostingResponse::from)
                .toList();
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
                return JobPostingResponse.from(inserted);
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
