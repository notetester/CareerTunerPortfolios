package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.CompanyAnalysis;
import com.careertuner.applicationcase.domain.FitAnalysis;
import com.careertuner.applicationcase.domain.JobAnalysis;
import com.careertuner.applicationcase.domain.JobPosting;
import com.careertuner.applicationcase.dto.CompanyAnalysisResponse;
import com.careertuner.applicationcase.dto.JobAnalysisResponse;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;

class ApplicationCaseServiceImplTest {

    @Test
    void createJobAnalysisStoresOnlyBAnalysisAndUsageLog() {
        ApplicationCaseMapper mapper = mock(ApplicationCaseMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        JobPostingFileStorage fileStorage = mock(JobPostingFileStorage.class);
        JobPostingTextExtractor textExtractor = mock(JobPostingTextExtractor.class);
        ApplicationCaseServiceImpl service = new ApplicationCaseServiceImpl(
                mapper, openAiClient, usageLogService, fileStorage, textExtractor);

        ApplicationCase applicationCase = ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("테스트기업")
                .jobTitle("백엔드 개발자")
                .build();
        JobPosting posting = JobPosting.builder()
                .applicationCaseId(10L)
                .extractedText("Java Spring REST API")
                .sourceType("TEXT")
                .build();
        Usage usage = new Usage("gpt-test", 100, 50, 150);
        JobAnalysisPayload payload = new JobAnalysisPayload(
                "정규직",
                "신입",
                "[\"Java\",\"Spring\"]",
                "[\"MySQL\"]",
                "REST API 개발",
                "Java와 Spring 이해",
                "NORMAL",
                "백엔드 개발자 공고입니다.",
                usage);

        when(mapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(mapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(openAiClient.analyzeJobPosting(applicationCase, "Java Spring REST API")).thenReturn(payload);
        when(mapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(JobAnalysis.builder()
                .id(20L)
                .applicationCaseId(10L)
                .employmentType("정규직")
                .experienceLevel("신입")
                .requiredSkills("[\"Java\",\"Spring\"]")
                .preferredSkills("[\"MySQL\"]")
                .difficulty("NORMAL")
                .summary("백엔드 개발자 공고입니다.")
                .build());

        JobAnalysisResponse response = service.createJobAnalysis(1L, 10L);

        assertThat(response.id()).isEqualTo(20L);
        verify(mapper).deleteJobAnalysesByCaseId(10L);
        verify(mapper).insertJobAnalysis(any(JobAnalysis.class));
        verify(mapper, never()).insertFitAnalysis(any(FitAnalysis.class));
        verify(usageLogService).recordSuccess(1L, 10L, "JOB_ANALYSIS", usage);
    }

    @Test
    void createCompanyAnalysisLimitsIndustryToDatabaseColumnLength() {
        ApplicationCaseMapper mapper = mock(ApplicationCaseMapper.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        AiUsageLogService usageLogService = mock(AiUsageLogService.class);
        JobPostingFileStorage fileStorage = mock(JobPostingFileStorage.class);
        JobPostingTextExtractor textExtractor = mock(JobPostingTextExtractor.class);
        ApplicationCaseServiceImpl service = new ApplicationCaseServiceImpl(
                mapper, openAiClient, usageLogService, fileStorage, textExtractor);

        ApplicationCase applicationCase = ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .build();
        JobPosting posting = JobPosting.builder()
                .applicationCaseId(10L)
                .extractedText("Backend platform job posting")
                .sourceType("TEXT")
                .build();
        Usage usage = new Usage("gpt-test", 100, 50, 150);
        String longIndustry = "A".repeat(130);
        CompanyAnalysisPayload payload = new CompanyAnalysisPayload(
                "Company summary",
                "Recent issues",
                longIndustry,
                "[]",
                "Interview points",
                "[]",
                usage);

        when(mapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase);
        when(mapper.findLatestJobPostingByCaseId(10L)).thenReturn(posting);
        when(openAiClient.analyzeCompany(applicationCase, "Backend platform job posting")).thenReturn(payload);
        when(mapper.findLatestCompanyAnalysisByCaseId(10L)).thenReturn(CompanyAnalysis.builder()
                .id(20L)
                .applicationCaseId(10L)
                .industry("A".repeat(100))
                .build());

        CompanyAnalysisResponse response = service.createCompanyAnalysis(1L, 10L);

        ArgumentCaptor<CompanyAnalysis> companyAnalysisCaptor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(mapper).insertCompanyAnalysis(companyAnalysisCaptor.capture());
        assertThat(companyAnalysisCaptor.getValue().getIndustry()).isEqualTo("A".repeat(100));
        assertThat(response.id()).isEqualTo(20L);
        verify(usageLogService).recordSuccess(1L, 10L, "COMPANY_RESEARCH", usage);
    }
}
