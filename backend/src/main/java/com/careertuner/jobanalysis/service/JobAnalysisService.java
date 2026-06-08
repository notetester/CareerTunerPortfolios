package com.careertuner.jobanalysis.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.applicationcase.service.OpenAiResponsesClient;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobanalysis.domain.JobAnalysis;
import com.careertuner.jobanalysis.dto.JobAnalysisReviewRequest;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;
import com.careertuner.jobposting.domain.JobPosting;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobAnalysisService {

    private static final String FEATURE_JOB_ANALYSIS = "JOB_ANALYSIS";

    private final ApplicationCaseAccessService accessService;
    private final JobAnalysisMapper jobAnalysisMapper;
    private final OpenAiResponsesClient openAiClient;
    private final AiUsageLogService aiUsageLogService;

    @Transactional
    public JobAnalysisResponse createJobAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = accessService.requireOwned(userId, applicationCaseId);
        JobPosting jobPosting = accessService.latestPostingRequired(applicationCaseId);
        String sourceText = accessService.sourceText(jobPosting);
        try {
            JobAnalysisPayload payload = openAiClient.analyzeJobPosting(
                    applicationCase,
                    sourceText);
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
                    .build();
            jobAnalysisMapper.insertJobAnalysis(jobAnalysis);
            aiUsageLogService.recordSuccess(userId, applicationCaseId, FEATURE_JOB_ANALYSIS, payload.usage());
            return JobAnalysisResponse.from(jobAnalysisMapper.findLatestJobAnalysisByCaseId(applicationCaseId));
        } catch (RuntimeException ex) {
            aiUsageLogService.recordFailure(userId, applicationCaseId, FEATURE_JOB_ANALYSIS, ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public JobAnalysisResponse createMockJobAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = accessService.requireOwned(userId, applicationCaseId);
        return JobAnalysisResponse.from(createMockJobAnalysisEntity(applicationCase, accessService.sourceText(applicationCaseId)));
    }

    @Transactional(readOnly = true)
    public JobAnalysisResponse getJobAnalysis(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        return JobAnalysisResponse.from(jobAnalysisMapper.findLatestJobAnalysisByCaseId(applicationCaseId));
    }

    @Transactional(readOnly = true)
    public List<JobAnalysisResponse> getJobAnalysisHistory(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        return jobAnalysisMapper.findJobAnalysisHistoryByCaseId(applicationCaseId).stream()
                .map(JobAnalysisResponse::from)
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
                .confirmedAt(Boolean.TRUE.equals(request.confirmed()) ? LocalDateTime.now() : existing.getConfirmedAt())
                .adminMemo(existing.getAdminMemo())
                .build();
        jobAnalysisMapper.updateJobAnalysisReview(updated);
        return JobAnalysisResponse.from(jobAnalysisMapper.findJobAnalysisByIdAndCaseId(analysisId, applicationCaseId));
    }

    public JobAnalysis createMockJobAnalysisEntity(ApplicationCase applicationCase, String sourceText) {
        Long applicationCaseId = applicationCase.getId();
        MockAnalysisSeed seed = MockAnalysisSeed.from(applicationCase, sourceText);

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
        jobAnalysisMapper.insertJobAnalysis(jobAnalysis);
        return jobAnalysisMapper.findLatestJobAnalysisByCaseId(applicationCaseId);
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record MockAnalysisSeed(
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
        public static MockAnalysisSeed from(ApplicationCase applicationCase, String sourceText) {
            String lowerText = sourceText == null ? "" : sourceText.toLowerCase(Locale.ROOT);
            boolean frontend = containsAny(lowerText, "react", "typescript", "javascript", "프론트");
            boolean backend = containsAny(lowerText, "java", "spring", "api", "서버", "백엔드");
            boolean cloud = containsAny(lowerText, "aws", "cloud", "배포", "kubernetes");

            String requiredSkills = frontend
                    ? "[\"React\",\"JavaScript\",\"REST API\"]"
                    : backend ? "[\"Java\",\"Spring\",\"SQL\"]" : "[\"문제 해결\",\"협업\",\"문서화\"]";
            String preferredSkills = cloud
                    ? "[\"AWS\",\"TypeScript\",\"CI/CD\"]"
                    : frontend ? "[\"TypeScript\",\"Next.js\",\"성능 최적화\"]" : "[\"데이터 분석\",\"프로젝트 경험\"]";
            String matchedSkills = frontend ? "[\"React\",\"REST API\",\"Git\"]" : backend ? "[\"Java\",\"SQL\"]" : "[\"협업\",\"문서화\"]";
            String missingSkills = cloud ? "[\"AWS\",\"배포 자동화\"]" : frontend ? "[\"TypeScript\",\"성능 최적화\"]" : "[\"정량 성과 정리\"]";
            int fitScore = frontend ? 72 : backend ? 64 : 58;

            return new MockAnalysisSeed(
                    "정규직",
                    "신입~경력 3년",
                    requiredSkills,
                    preferredSkills,
                    "%s %s 직무의 주요 업무를 공고 요구사항 기준으로 정리한 mock 분석입니다."
                            .formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle()),
                    "공고 원문 기반 자격 요건을 정리한 mock 분석입니다.",
                    fitScore >= 70 ? "NORMAL" : "HARD",
                    "지원 건과 공고문을 바탕으로 생성한 개발용 mock 공고 분석입니다.",
                    fitScore,
                    matchedSkills,
                    missingSkills,
                    "[\"공고 키워드로 프로젝트 경험 재정리\",\"부족 역량을 작은 실습으로 보완\",\"면접 답변에 수치와 역할 추가\"]",
                    "[]",
                    "강점은 직무 요구사항과 연결하고, 부족 역량은 학습 계획과 포트폴리오 보완으로 설명하는 전략이 적절합니다.");
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
