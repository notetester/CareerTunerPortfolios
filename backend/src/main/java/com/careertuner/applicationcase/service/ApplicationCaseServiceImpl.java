package com.careertuner.applicationcase.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.CompanyAnalysis;
import com.careertuner.applicationcase.domain.FitAnalysis;
import com.careertuner.applicationcase.domain.JobAnalysis;
import com.careertuner.applicationcase.domain.JobPosting;
import com.careertuner.applicationcase.dto.AnalysisResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.dto.CompanyAnalysisResponse;
import com.careertuner.applicationcase.dto.CreateApplicationCaseRequest;
import com.careertuner.applicationcase.dto.FitAnalysisResponse;
import com.careertuner.applicationcase.dto.JobAnalysisResponse;
import com.careertuner.applicationcase.dto.JobPostingRequest;
import com.careertuner.applicationcase.dto.JobPostingResponse;
import com.careertuner.applicationcase.dto.UpdateApplicationCaseRequest;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApplicationCaseServiceImpl implements ApplicationCaseService {

    private static final String DEFAULT_SOURCE_TYPE = "TEXT";
    private static final String DEFAULT_STATUS = "DRAFT";
    private static final Set<String> SOURCE_TYPES = Set.of("TEXT", "PDF", "IMAGE", "URL", "MANUAL");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ANALYZING", "READY", "APPLIED", "CLOSED");

    private final ApplicationCaseMapper applicationCaseMapper;

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
        return ApplicationCaseResponse.from(requireOwned(userId, applicationCase.getId()));
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
        return ApplicationCaseResponse.from(requireOwned(userId, id));
    }

    @Override
    @Transactional
    public ApplicationCaseResponse update(Long userId, Long id, UpdateApplicationCaseRequest request) {
        ApplicationCase existing = requireOwned(userId, id);
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
        return ApplicationCaseResponse.from(requireOwned(userId, id));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        int deleted = applicationCaseMapper.deleteApplicationCase(id, userId);
        if (deleted == 0) {
            throw notFound();
        }
    }

    @Override
    @Transactional
    public JobPostingResponse saveJobPosting(Long userId, Long applicationCaseId, JobPostingRequest request) {
        requireOwned(userId, applicationCaseId);
        validateJobPosting(request);

        applicationCaseMapper.deleteJobPostingsByCaseId(applicationCaseId);
        JobPosting jobPosting = JobPosting.builder()
                .applicationCaseId(applicationCaseId)
                .originalText(blankToNull(request.originalText()))
                .uploadedFileUrl(blankToNull(request.uploadedFileUrl()))
                .extractedText(blankToNull(request.extractedText()))
                .sourceType(normalizeOption(request.sourceType(), DEFAULT_SOURCE_TYPE, SOURCE_TYPES, "sourceType"))
                .build();
        applicationCaseMapper.insertJobPosting(jobPosting);
        return JobPostingResponse.from(applicationCaseMapper.findLatestJobPostingByCaseId(applicationCaseId));
    }

    @Override
    @Transactional(readOnly = true)
    public JobPostingResponse getJobPosting(Long userId, Long applicationCaseId) {
        requireOwned(userId, applicationCaseId);
        JobPosting jobPosting = applicationCaseMapper.findLatestJobPostingByCaseId(applicationCaseId);
        if (jobPosting == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고문을 찾을 수 없습니다.");
        }
        return JobPostingResponse.from(jobPosting);
    }

    @Override
    @Transactional
    public JobAnalysisResponse createMockJobAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = requireOwned(userId, applicationCaseId);
        JobAnalysis jobAnalysis = createJobAnalysis(applicationCase, sourceText(applicationCaseId));
        return JobAnalysisResponse.from(jobAnalysis);
    }

    @Override
    @Transactional(readOnly = true)
    public JobAnalysisResponse getJobAnalysis(Long userId, Long applicationCaseId) {
        requireOwned(userId, applicationCaseId);
        return JobAnalysisResponse.from(applicationCaseMapper.findLatestJobAnalysisByCaseId(applicationCaseId));
    }

    @Override
    @Transactional
    public CompanyAnalysisResponse createMockCompanyAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = requireOwned(userId, applicationCaseId);
        CompanyAnalysisSeed seed = CompanyAnalysisSeed.from(applicationCase, sourceText(applicationCaseId));

        applicationCaseMapper.deleteCompanyAnalysesByCaseId(applicationCaseId);
        CompanyAnalysis companyAnalysis = CompanyAnalysis.builder()
                .applicationCaseId(applicationCaseId)
                .companySummary(seed.companySummary())
                .recentIssues(seed.recentIssues())
                .industry(seed.industry())
                .competitors(seed.competitors())
                .interviewPoints(seed.interviewPoints())
                .sources(seed.sources())
                .build();
        applicationCaseMapper.insertCompanyAnalysis(companyAnalysis);
        return CompanyAnalysisResponse.from(applicationCaseMapper.findLatestCompanyAnalysisByCaseId(applicationCaseId));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyAnalysisResponse getCompanyAnalysis(Long userId, Long applicationCaseId) {
        requireOwned(userId, applicationCaseId);
        return CompanyAnalysisResponse.from(applicationCaseMapper.findLatestCompanyAnalysisByCaseId(applicationCaseId));
    }

    @Override
    @Transactional
    public AnalysisResponse createMockAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = requireOwned(userId, applicationCaseId);
        String sourceText = sourceText(applicationCaseId);
        MockAnalysisSeed seed = MockAnalysisSeed.from(applicationCase, sourceText);

        applicationCaseMapper.deleteJobAnalysesByCaseId(applicationCaseId);
        applicationCaseMapper.deleteFitAnalysesByCaseId(applicationCaseId);

        JobAnalysis jobAnalysis = JobAnalysis.builder()
                .applicationCaseId(applicationCaseId)
                .employmentType(seed.employmentType())
                .experienceLevel(seed.experienceLevel())
                .requiredSkills(seed.requiredSkills())
                .preferredSkills(seed.preferredSkills())
                .duties(seed.duties())
                .qualifications(seed.qualifications())
                .difficulty(seed.difficulty())
                .summary(seed.summary())
                .build();
        applicationCaseMapper.insertJobAnalysis(jobAnalysis);

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
                applicationCaseMapper.findLatestJobAnalysisByCaseId(applicationCaseId),
                applicationCaseMapper.findLatestFitAnalysisByCaseId(applicationCaseId));
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = requireOwned(userId, applicationCaseId);
        return response(
                applicationCase,
                applicationCaseMapper.findLatestJobAnalysisByCaseId(applicationCaseId),
                applicationCaseMapper.findLatestFitAnalysisByCaseId(applicationCaseId));
    }

    private ApplicationCase requireOwned(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = applicationCaseMapper.findApplicationCaseByIdAndUserId(applicationCaseId, userId);
        if (applicationCase == null) {
            throw notFound();
        }
        return applicationCase;
    }

    private static AnalysisResponse response(ApplicationCase applicationCase, JobAnalysis jobAnalysis, FitAnalysis fitAnalysis) {
        return new AnalysisResponse(
                ApplicationCaseResponse.from(applicationCase),
                JobAnalysisResponse.from(jobAnalysis),
                FitAnalysisResponse.from(fitAnalysis));
    }

    private JobAnalysis createJobAnalysis(ApplicationCase applicationCase, String sourceText) {
        Long applicationCaseId = applicationCase.getId();
        MockAnalysisSeed seed = MockAnalysisSeed.from(applicationCase, sourceText);

        applicationCaseMapper.deleteJobAnalysesByCaseId(applicationCaseId);
        JobAnalysis jobAnalysis = JobAnalysis.builder()
                .applicationCaseId(applicationCaseId)
                .employmentType(seed.employmentType())
                .experienceLevel(seed.experienceLevel())
                .requiredSkills(seed.requiredSkills())
                .preferredSkills(seed.preferredSkills())
                .duties(seed.duties())
                .qualifications(seed.qualifications())
                .difficulty(seed.difficulty())
                .summary(seed.summary())
                .build();
        applicationCaseMapper.insertJobAnalysis(jobAnalysis);
        return applicationCaseMapper.findLatestJobAnalysisByCaseId(applicationCaseId);
    }

    private String sourceText(Long applicationCaseId) {
        JobPosting jobPosting = applicationCaseMapper.findLatestJobPostingByCaseId(applicationCaseId);
        return jobPosting != null
                ? defaultString(jobPosting.getExtractedText(), jobPosting.getOriginalText())
                : "";
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
    }

    private static void validateJobPosting(JobPostingRequest request) {
        if (isBlank(request.originalText()) && isBlank(request.extractedText()) && isBlank(request.uploadedFileUrl())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공고 원문, 추출 텍스트, 파일 URL 중 하나는 필요합니다.");
        }
    }

    private static String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
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

    private record MockAnalysisSeed(
            String employmentType,
            String experienceLevel,
            String requiredSkills,
            String preferredSkills,
            String duties,
            String qualifications,
            String difficulty,
            String summary,
            int fitScore,
            String matchedSkills,
            String missingSkills,
            String recommendedStudy,
            String recommendedCertificates,
            String strategy
    ) {
        static MockAnalysisSeed from(ApplicationCase applicationCase, String sourceText) {
            String lowerText = sourceText == null ? "" : sourceText.toLowerCase();
            boolean frontend = containsAny(lowerText, "react", "typescript", "javascript", "프론트");
            boolean backend = containsAny(lowerText, "java", "spring", "api", "서버", "백엔드");
            boolean cloud = containsAny(lowerText, "aws", "cloud", "배포", "kubernetes");

            String requiredSkills = frontend
                    ? "[\"React\",\"JavaScript\",\"REST API\"]"
                    : backend ? "[\"Java\",\"Spring\",\"SQL\"]" : "[\"문제 해결력\",\"협업\",\"문서화\"]";
            String preferredSkills = cloud
                    ? "[\"AWS\",\"TypeScript\",\"CI/CD\"]"
                    : frontend ? "[\"TypeScript\",\"Next.js\",\"웹 성능 최적화\"]" : "[\"데이터 분석\",\"프로젝트 경험\"]";
            String matchedSkills = frontend ? "[\"React\",\"REST API\",\"Git\"]" : backend ? "[\"Java\",\"SQL\"]" : "[\"협업\",\"문서화\"]";
            String missingSkills = cloud ? "[\"AWS\",\"배포 자동화\"]" : frontend ? "[\"TypeScript\",\"성능 최적화\"]" : "[\"정량 성과 정리\"]";
            int fitScore = frontend ? 72 : backend ? 64 : 58;

            return new MockAnalysisSeed(
                    "정규직",
                    "신입~경력 3년",
                    requiredSkills,
                    preferredSkills,
                    "%s %s 직무의 주요 업무와 공고 요구사항을 기반으로 초기 분석했습니다."
                            .formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle()),
                    "공고 원문 기반 자격 요건을 정리한 mock 분석입니다. 실제 AI 분석 연동 전까지 화면/흐름 검증에 사용합니다.",
                    fitScore >= 70 ? "NORMAL" : "HARD",
                    "지원 건과 공고문을 바탕으로 생성한 개발용 mock 공고 분석입니다.",
                    fitScore,
                    matchedSkills,
                    missingSkills,
                    "[\"공고 핵심 키워드로 프로젝트 경험 재정리\",\"부족 역량을 작은 실습으로 보완\",\"면접 답변에 수치와 역할 추가\"]",
                    "[]",
                    "강점은 직무 요구사항과 연결하고, 부족 역량은 학습 계획과 포트폴리오 보완으로 설명하는 전략이 적절합니다.");
        }

        private static boolean containsAny(String text, String... keywords) {
            for (String keyword : keywords) {
                if (text.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
    }

    private record CompanyAnalysisSeed(
            String companySummary,
            String recentIssues,
            String industry,
            String competitors,
            String interviewPoints,
            String sources
    ) {
        static CompanyAnalysisSeed from(ApplicationCase applicationCase, String sourceText) {
            String companyName = applicationCase.getCompanyName();
            String jobTitle = applicationCase.getJobTitle();
            String lowerText = (companyName + " " + jobTitle + " " + defaultString(sourceText, ""))
                    .toLowerCase(Locale.ROOT);

            boolean fintech = containsAny(lowerText, "pay", "페이", "금융", "핀테크", "bank", "은행");
            boolean platform = containsAny(lowerText, "platform", "플랫폼", "commerce", "커머스", "서비스");
            boolean cloud = containsAny(lowerText, "cloud", "aws", "인프라", "배포");

            String industry = fintech
                    ? "핀테크/금융 플랫폼"
                    : platform ? "디지털 플랫폼 서비스" : "IT 서비스";
            String competitors = fintech
                    ? "[\"토스\",\"네이버파이낸셜\",\"카카오페이\"]"
                    : platform ? "[\"네이버\",\"카카오\",\"쿠팡\"]" : "[\"동종 IT 서비스 기업\",\"SI/솔루션 기업\"]";
            String recentIssues = cloud
                    ? "공고 키워드상 클라우드 운영, 안정성, 자동화 경험을 중요하게 볼 가능성이 있습니다."
                    : "공고와 직무 정보를 기준으로 서비스 성장성, 사용자 경험, 데이터 기반 개선 역량을 확인해야 합니다.";

            return new CompanyAnalysisSeed(
                    "%s는 %s 직무 관점에서 제품 이해도와 실행 경험을 함께 확인할 가능성이 높은 기업입니다."
                            .formatted(companyName, jobTitle),
                    recentIssues,
                    industry,
                    competitors,
                    "면접에서는 회사 서비스 이해, 직무 관련 프로젝트 경험, 협업 상황에서의 문제 해결 방식을 구체적으로 준비하는 것이 좋습니다.",
                    "[\"지원 건 기본 정보\",\"공고문 텍스트\",\"개발용 mock seed\"]");
        }

        private static boolean containsAny(String text, String... keywords) {
            for (String keyword : keywords) {
                if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }
}
