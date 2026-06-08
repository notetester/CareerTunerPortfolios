package com.careertuner.applicationcase.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.FitAnalysis;
import com.careertuner.applicationcase.dto.AnalysisResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.dto.CreateApplicationCaseRequest;
import com.careertuner.applicationcase.dto.FitAnalysisResponse;
import com.careertuner.applicationcase.dto.UpdateApplicationCaseRequest;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.service.CompanyAnalysisService;
import com.careertuner.jobanalysis.domain.JobAnalysis;
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
    private static final Set<String> SOURCE_TYPES = Set.of("TEXT", "PDF", "IMAGE", "URL", "MANUAL");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ANALYZING", "READY", "APPLIED", "CLOSED");

    private final ApplicationCaseMapper applicationCaseMapper;
    private final ApplicationCaseAccessService accessService;
    private final JobPostingService jobPostingService;
    private final JobAnalysisService jobAnalysisService;
    private final CompanyAnalysisService companyAnalysisService;
    private final JobAnalysisMapper jobAnalysisMapper;

    @Override
    @Transactional
    public ApplicationCaseResponse create(Long userId, CreateApplicationCaseRequest request) {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .userId(userId)
                .companyName(request.companyName().trim())
                .jobTitle(request.jobTitle().trim())
                .postingDate(request.postingDate())
                .sourceType(normalizeOption(request.sourceType(), DEFAULT_SOURCE_TYPE, SOURCE_TYPES, "sourceType"))
                .status(normalizeOption(request.status(), DEFAULT_STATUS, STATUSES, "status"))
                .favorite(Boolean.TRUE.equals(request.favorite()))
                .build();
        applicationCaseMapper.insertApplicationCase(applicationCase);
        return ApplicationCaseResponse.from(accessService.requireOwned(userId, applicationCase.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationCaseResponse> list(Long userId) {
        return applicationCaseMapper.findApplicationCasesByUserId(userId).stream()
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
        ApplicationCase updated = ApplicationCase.builder()
                .id(existing.getId())
                .userId(userId)
                .companyName(defaultString(request.companyName(), existing.getCompanyName()))
                .jobTitle(defaultString(request.jobTitle(), existing.getJobTitle()))
                .postingDate(request.postingDate() != null ? request.postingDate() : existing.getPostingDate())
                .sourceType(normalizeOption(request.sourceType(), existing.getSourceType(), SOURCE_TYPES, "sourceType"))
                .status(normalizeOption(request.status(), existing.getStatus(), STATUSES, "status"))
                .favorite(request.favorite() != null ? request.favorite() : existing.isFavorite())
                .build();
        applicationCaseMapper.updateApplicationCase(updated);
        return ApplicationCaseResponse.from(accessService.requireOwned(userId, id));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        int deleted = applicationCaseMapper.deleteApplicationCase(id, userId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
        }
    }

    @Override
    public JobPostingResponse saveJobPosting(Long userId, Long applicationCaseId, JobPostingRequest request) {
        return jobPostingService.saveJobPosting(userId, applicationCaseId, request);
    }

    @Override
    public JobPostingResponse uploadJobPostingFile(Long userId, Long applicationCaseId, MultipartFile file, String sourceType) {
        return jobPostingService.uploadJobPostingFile(userId, applicationCaseId, file, sourceType);
    }

    @Override
    public JobPostingResponse getJobPosting(Long userId, Long applicationCaseId) {
        return jobPostingService.getJobPosting(userId, applicationCaseId);
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

    private static AnalysisResponse response(ApplicationCase applicationCase, JobAnalysis jobAnalysis, FitAnalysis fitAnalysis) {
        return new AnalysisResponse(
                ApplicationCaseResponse.from(applicationCase),
                JobAnalysisResponse.from(jobAnalysis),
                FitAnalysisResponse.from(fitAnalysis));
    }

    private static String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
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
