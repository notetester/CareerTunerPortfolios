package com.careertuner.companyanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.applicationcase.service.ApplicationCaseAnalysisStatusService;
import com.careertuner.applicationcase.service.BAnalysisGenerationService;
import com.careertuner.applicationcase.service.BAnalysisJsonValidator;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.dto.CompanyAnalysisReviewRequest;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.companyanalysis.websearch.CompanyEvidenceCollector;
import com.careertuner.companyanalysis.websearch.CompanySourceResolver;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchClient;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchProperties;
import com.careertuner.jobposting.mapper.JobPostingMapper;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

class CompanyAnalysisServiceReviewValidationTest {

    @Test
    void reviewCompanyAnalysisRejectsVerifiedFactsStringArray() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        CompanyAnalysisService service = companyAnalysisService(applicationCaseMapper, jobPostingMapper, companyAnalysisMapper);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase());
        when(companyAnalysisMapper.findCompanyAnalysisByIdAndCaseId(20L, 10L)).thenReturn(existingAnalysis());

        CompanyAnalysisReviewRequest request = new CompanyAnalysisReviewRequest(
                null, null, null, null, null, null,
                "[\"B2B platform\"]", null, null);

        assertThatThrownBy(() -> service.reviewCompanyAnalysis(1L, 10L, 20L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT))
                .hasMessageContaining("JSON");

        verify(companyAnalysisMapper, never()).updateCompanyAnalysisReview(any(CompanyAnalysis.class));
    }

    @Test
    void reviewCompanyAnalysisAcceptsVerifiedFactsObjectArray() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        CompanyAnalysisService service = companyAnalysisService(applicationCaseMapper, jobPostingMapper, companyAnalysisMapper);
        String verifiedFacts = "[{\"fact\":\"B2B platform\",\"source\":\"job posting\"}]";

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase());
        when(companyAnalysisMapper.findCompanyAnalysisByIdAndCaseId(20L, 10L))
                .thenReturn(existingAnalysis())
                .thenReturn(existingAnalysisWithVerifiedFacts(verifiedFacts));

        CompanyAnalysisReviewRequest request = new CompanyAnalysisReviewRequest(
                null, null, null, null, null, null,
                verifiedFacts, null, null);

        CompanyAnalysisResponse response = service.reviewCompanyAnalysis(1L, 10L, 20L, request);

        ArgumentCaptor<CompanyAnalysis> analysisCaptor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper).updateCompanyAnalysisReview(analysisCaptor.capture());
        assertThat(analysisCaptor.getValue().getVerifiedFacts()).isEqualTo(verifiedFacts);
        assertThat(response.verifiedFacts()).isEqualTo(verifiedFacts);
    }

    @Test
    void reviewCompanyAnalysisRejectsAiInferencesWithoutBasis() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        CompanyAnalysisService service = companyAnalysisService(applicationCaseMapper, jobPostingMapper, companyAnalysisMapper);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase());
        when(companyAnalysisMapper.findCompanyAnalysisByIdAndCaseId(20L, 10L)).thenReturn(existingAnalysis());

        CompanyAnalysisReviewRequest request = new CompanyAnalysisReviewRequest(
                null, null, null, null, null, null,
                null, "[{\"inference\":\"platform operations may be discussed\"}]", null);

        assertThatThrownBy(() -> service.reviewCompanyAnalysis(1L, 10L, 20L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT))
                .hasMessageContaining("JSON");

        verify(companyAnalysisMapper, never()).updateCompanyAnalysisReview(any(CompanyAnalysis.class));
    }

    @Test
    void reviewCompanyAnalysisAcceptsAiInferencesWithBasis() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        CompanyAnalysisService service = companyAnalysisService(applicationCaseMapper, jobPostingMapper, companyAnalysisMapper);
        String aiInferences = "[{\"inference\":\"platform operations may be discussed\",\"basis\":\"job posting mentions B2B platform\"}]";

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase());
        when(companyAnalysisMapper.findCompanyAnalysisByIdAndCaseId(20L, 10L))
                .thenReturn(existingAnalysis())
                .thenReturn(existingAnalysisWithAiInferences(aiInferences));

        CompanyAnalysisReviewRequest request = new CompanyAnalysisReviewRequest(
                null, null, null, null, null, null,
                null, aiInferences, null);

        CompanyAnalysisResponse response = service.reviewCompanyAnalysis(1L, 10L, 20L, request);

        ArgumentCaptor<CompanyAnalysis> analysisCaptor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper).updateCompanyAnalysisReview(analysisCaptor.capture());
        assertThat(analysisCaptor.getValue().getAiInferences()).isEqualTo(aiInferences);
        assertThat(response.aiInferences()).isEqualTo(aiInferences);
    }

    @Test
    void reviewCompanyAnalysisReattachesUnknownMarkersOwnedByServer() {
        // 응답에서 kind=UNKNOWN 마커가 분리된 채 편집되므로, 검수 저장 시 서버가 기존 마커를
        // 재부착해야 unknown 이 일반 추론으로 오염되거나 유실되지 않는다(6단계 1차안).
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        CompanyAnalysisService service = companyAnalysisService(applicationCaseMapper, jobPostingMapper, companyAnalysisMapper);
        String existingAiInferences = "[{\"inference\":\"기존 추론\",\"basis\":\"기존 근거\"},"
                + "{\"inference\":\"매출 규모는 현재 입력 자료로 확인되지 않습니다.\",\"basis\":\"공고문에 관련 정보가 없다\","
                + "\"kind\":\"UNKNOWN\",\"topic\":\"매출 규모\"}]";
        String editedAiInferences = "[{\"inference\":\"사용자 수정 추론\",\"basis\":\"사용자 근거\"}]";

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase());
        when(companyAnalysisMapper.findCompanyAnalysisByIdAndCaseId(20L, 10L))
                .thenReturn(existingAnalysisWithAiInferences(existingAiInferences));

        CompanyAnalysisReviewRequest request = new CompanyAnalysisReviewRequest(
                null, null, null, null, null, null,
                null, editedAiInferences, null);

        CompanyAnalysisResponse response = service.reviewCompanyAnalysis(1L, 10L, 20L, request);

        ArgumentCaptor<CompanyAnalysis> analysisCaptor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper).updateCompanyAnalysisReview(analysisCaptor.capture());
        String stored = analysisCaptor.getValue().getAiInferences();
        assertThat(stored).contains("사용자 수정 추론").contains("\"kind\":\"UNKNOWN\"").contains("매출 규모");
        // 응답에서는 마커가 다시 분리돼 virtual unknowns 로 내려간다.
        assertThat(response.aiInferences()).doesNotContain("UNKNOWN");
        assertThat(response.unknowns()).contains("매출 규모");
    }

    @Test
    void companyReviewRequestDoesNotExposeSourceMetadataFields() {
        Set<String> componentNames = Arrays.stream(CompanyAnalysisReviewRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(componentNames)
                .doesNotContain("sourceType", "checkedAt", "refreshRecommendedAt");
    }

    @Test
    void companyReviewMapperDoesNotUpdateSourceMetadataColumns() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/companyanalysis/CompanyAnalysisMapper.xml"));
        String updateStartToken = "<update id=\"updateCompanyAnalysisReview\">";
        int updateStart = xml.indexOf(updateStartToken);
        int updateEnd = xml.indexOf("</update>", updateStart);

        assertThat(updateStart).isNotNegative();
        assertThat(updateEnd).isGreaterThan(updateStart);
        String updateSql = xml.substring(updateStart, updateEnd);

        assertThat(updateSql)
                .doesNotContain("source_type", "checked_at", "refresh_recommended_at");
    }

    private static CompanyAnalysisService companyAnalysisService(ApplicationCaseMapper applicationCaseMapper,
                                                                 JobPostingMapper jobPostingMapper,
                                                                 CompanyAnalysisMapper companyAnalysisMapper) {
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        return new CompanyAnalysisService(
                accessService,
                companyAnalysisMapper,
                mock(BAnalysisGenerationService.class),
                mock(AiUsageLogService.class),
                mock(ApplicationCaseAnalysisStatusService.class),
                mock(TransactionTemplate.class),
                new BAnalysisJsonValidator(new ObjectMapper()),
                new BCompanyAnalysisCanonicalizer(new ObjectMapper()),
                mock(NotificationService.class),
                new CompanyWebSearchProperties(),
                mock(CompanySourceResolver.class),
                mock(CompanyWebSearchClient.class),
                mock(CompanyEvidenceCollector.class),
                mock(CompanySearchCacheService.class),
                new ObjectMapper());
    }

    private static ApplicationCase applicationCase() {
        return ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .build();
    }

    private static CompanyAnalysis existingAnalysis() {
        return existingAnalysisWithVerifiedFacts("[]");
    }

    private static CompanyAnalysis existingAnalysisWithVerifiedFacts(String verifiedFacts) {
        return existingAnalysisWithVerifiedFactsAndAiInferences(verifiedFacts, "[]");
    }

    private static CompanyAnalysis existingAnalysisWithAiInferences(String aiInferences) {
        return existingAnalysisWithVerifiedFactsAndAiInferences("[]", aiInferences);
    }

    private static CompanyAnalysis existingAnalysisWithVerifiedFactsAndAiInferences(String verifiedFacts, String aiInferences) {
        LocalDateTime checkedAt = LocalDateTime.of(2026, 6, 10, 9, 0);
        return CompanyAnalysis.builder()
                .id(20L)
                .applicationCaseId(10L)
                .jobPostingId(30L)
                .jobPostingRevision(2)
                .companySummary("Company summary")
                .recentIssues("Recent issues")
                .industry("IT services")
                .competitors("[]")
                .interviewPoints("Interview points")
                .sources("[]")
                .verifiedFacts(verifiedFacts)
                .aiInferences(aiInferences)
                .sourceType("JOB_POSTING")
                .checkedAt(checkedAt)
                .refreshRecommendedAt(checkedAt.plusDays(30))
                .build();
    }
}
