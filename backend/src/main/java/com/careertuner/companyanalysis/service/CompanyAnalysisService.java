package com.careertuner.companyanalysis.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.applicationcase.service.OpenAiResponsesClient;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyAnalysisService {

    private static final String FEATURE_COMPANY_RESEARCH = "COMPANY_RESEARCH";
    private static final int COMPANY_INDUSTRY_MAX_LENGTH = 100;

    private final ApplicationCaseAccessService accessService;
    private final CompanyAnalysisMapper companyAnalysisMapper;
    private final OpenAiResponsesClient openAiClient;
    private final AiUsageLogService aiUsageLogService;

    @Transactional
    public CompanyAnalysisResponse createCompanyAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = accessService.requireOwned(userId, applicationCaseId);
        try {
            CompanyAnalysisPayload payload = openAiClient.analyzeCompany(
                    applicationCase,
                    accessService.sourceTextRequired(applicationCaseId));
            companyAnalysisMapper.deleteCompanyAnalysesByCaseId(applicationCaseId);
            CompanyAnalysis companyAnalysis = CompanyAnalysis.builder()
                    .applicationCaseId(applicationCaseId)
                    .companySummary(blankToNull(payload.companySummary()))
                    .recentIssues(blankToNull(payload.recentIssues()))
                    .industry(compactColumnText(payload.industry(), COMPANY_INDUSTRY_MAX_LENGTH))
                    .competitors(payload.competitors())
                    .interviewPoints(blankToNull(payload.interviewPoints()))
                    .sources(payload.sources())
                    .build();
            companyAnalysisMapper.insertCompanyAnalysis(companyAnalysis);
            aiUsageLogService.recordSuccess(userId, applicationCaseId, FEATURE_COMPANY_RESEARCH, payload.usage());
            return CompanyAnalysisResponse.from(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(applicationCaseId));
        } catch (RuntimeException ex) {
            aiUsageLogService.recordFailure(userId, applicationCaseId, FEATURE_COMPANY_RESEARCH, ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public CompanyAnalysisResponse createMockCompanyAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = accessService.requireOwned(userId, applicationCaseId);
        CompanyAnalysisSeed seed = CompanyAnalysisSeed.from(applicationCase, accessService.sourceText(applicationCaseId));

        companyAnalysisMapper.deleteCompanyAnalysesByCaseId(applicationCaseId);
        CompanyAnalysis companyAnalysis = CompanyAnalysis.builder()
                .applicationCaseId(applicationCaseId)
                .companySummary(seed.companySummary())
                .recentIssues(seed.recentIssues())
                .industry(seed.industry())
                .competitors(seed.competitors())
                .interviewPoints(seed.interviewPoints())
                .sources(seed.sources())
                .build();
        companyAnalysisMapper.insertCompanyAnalysis(companyAnalysis);
        return CompanyAnalysisResponse.from(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(applicationCaseId));
    }

    @Transactional(readOnly = true)
    public CompanyAnalysisResponse getCompanyAnalysis(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        return CompanyAnalysisResponse.from(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(applicationCaseId));
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
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
                    "%s는 %s 직무 지원에서 제품 이해와 실행 경험을 함께 확인할 가능성이 높은 기업입니다."
                            .formatted(companyName, jobTitle),
                    recentIssues,
                    industry,
                    competitors,
                    "면접에서는 회사 서비스 이해, 직무 관련 프로젝트 경험, 협업 상황에서의 문제 해결 방식을 구체적으로 준비하는 것이 좋습니다.",
                    "[\"지원 건 기본 정보\",\"공고문 텍스트\",\"개발용 mock seed\"]");
        }

        private static String defaultString(String value, String defaultValue) {
            return value == null || value.isBlank() ? defaultValue : value.trim();
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
