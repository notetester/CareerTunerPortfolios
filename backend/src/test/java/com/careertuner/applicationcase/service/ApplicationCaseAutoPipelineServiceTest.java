package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.fitanalysis.ai.MockFitAnalysisAiService;
import com.careertuner.fitanalysis.domain.FitAnalysisGenerationSource;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.jobanalysis.domain.JobAnalysis;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;

import tools.jackson.databind.ObjectMapper;

class ApplicationCaseAutoPipelineServiceTest {

    @Test
    void runAfterExtractionPassCreatesSelfHostedAnalysisFitStrategyAndInterviewPrep() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        FitAnalysisMapper fitAnalysisMapper = mock(FitAnalysisMapper.class);
        InterviewMapper interviewMapper = mock(InterviewMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        BAnalysisProperties properties = new BAnalysisProperties();
        BAnalysisGenerationService bAnalysisGenerationService = new BAnalysisGenerationService(
                properties,
                mock(BLocalLlmClient.class),
                new BJobSentenceClassifier(),
                objectMapper,
                mock(BAnthropicClient.class),
                mock(OpenAiResponsesClient.class));
        com.careertuner.companyanalysis.service.CompanyAnalysisService companyAnalysisService =
                mock(com.careertuner.companyanalysis.service.CompanyAnalysisService.class);
        ApplicationCaseAutoPipelineService service = new ApplicationCaseAutoPipelineService(
                applicationCaseMapper,
                jobAnalysisMapper,
                companyAnalysisMapper,
                fitAnalysisMapper,
                interviewMapper,
                new MockFitAnalysisAiService(),
                objectMapper,
                bAnalysisGenerationService,
                new com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer(objectMapper),
                companyAnalysisService);
        String postingText = """
                Acme is hiring a Backend Engineer.
                Responsibilities: build Spring APIs, operate MySQL services, and improve Docker deployment.
                Qualifications: Java, Spring Boot, MyBatis, MySQL, REST, Docker, Testing.
                Preferred: React and TypeScript collaboration experience.
                """.repeat(3);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Acme")
                .jobTitle("Backend Engineer")
                .sourceType("PDF")
                .status("DRAFT")
                .build());
        when(applicationCaseMapper.markAnalysisStarted(10L, 1L, "DRAFT")).thenReturn(1);
        when(applicationCaseMapper.markReadyAfterAnalysis(10L, 1L, "DRAFT")).thenReturn(1);
        when(fitAnalysisMapper.findGenerationSource(1L, 10L)).thenReturn(fitSource());
        doAnswer(invocation -> {
            invocation.<FitAnalysisResult>getArgument(0).setId(70L);
            return null;
        }).when(fitAnalysisMapper).insertFitAnalysis(any(FitAnalysisResult.class));
        doAnswer(invocation -> {
            invocation.<InterviewSession>getArgument(0).setId(80L);
            return null;
        }).when(interviewMapper).insertSession(any(InterviewSession.class));

        service.runAfterExtractionPass(1L, 10L, 20L, 2, postingText);

        ArgumentCaptor<JobAnalysis> jobCaptor = ArgumentCaptor.forClass(JobAnalysis.class);
        verify(jobAnalysisMapper).insertJobAnalysis(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getJobPostingId()).isEqualTo(20L);
        assertThat(jobCaptor.getValue().getJobPostingRevision()).isEqualTo(2);
        assertThat(jobCaptor.getValue().getRequiredSkills()).contains("Java", "Spring Boot", "MySQL");

        ArgumentCaptor<CompanyAnalysis> companyCaptor = ArgumentCaptor.forClass(CompanyAnalysis.class);
        verify(companyAnalysisMapper).insertCompanyAnalysis(companyCaptor.capture());
        assertThat(companyCaptor.getValue().getSourceType()).isEqualTo("JOB_POSTING");
        assertThat(companyCaptor.getValue().getCompanySummary()).contains("외부 기업 정보나 OpenAI 폴백은 사용하지 않았습니다");
        assertThat(companyCaptor.getValue().getCompanySummary())
                .doesNotContain("information was summarized", "No external company API");
        // 자동 파이프라인이 사용자 직접 경로와 동일한 웹검색(collectWebEvidence)을 호출하는지 잠근다(웹검색 배선).
        verify(companyAnalysisService).collectWebEvidence(any(ApplicationCase.class));

        ArgumentCaptor<FitAnalysisResult> fitCaptor = ArgumentCaptor.forClass(FitAnalysisResult.class);
        verify(fitAnalysisMapper).insertFitAnalysis(fitCaptor.capture());
        assertThat(fitCaptor.getValue().getModel()).isEqualTo("self-rules-v1");
        assertThat(fitCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        verify(fitAnalysisMapper).insertHistory(eq(70L), eq(10L), eq(null), any(), any());
        verify(fitAnalysisMapper, atLeastOnce()).insertConditionMatch(eq(70L), any(), any(), anyInt());
        verify(fitAnalysisMapper, atLeastOnce()).insertLearningTask(any());

        verify(interviewMapper).insertSession(any(InterviewSession.class));
        verify(interviewMapper, atLeastOnce()).insertQuestion(any(InterviewQuestion.class));
        verify(applicationCaseMapper).markReadyAfterAnalysis(10L, 1L, "DRAFT");

        ArgumentCaptor<AiUsageLog> usageCaptor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(applicationCaseMapper, atLeastOnce()).insertAiUsageLog(usageCaptor.capture());
        assertThat(usageCaptor.getAllValues())
                .extracting(AiUsageLog::getFeatureType)
                .contains("JOB_ANALYSIS", "COMPANY_RESEARCH", "FIT_ANALYSIS", "INTERVIEW_QUESTION_GEN");
        assertThat(usageCaptor.getAllValues())
                .allMatch(log -> "self-rules-v1".equals(log.getModel()))
                .allMatch(log -> Integer.valueOf(0).equals(log.getCreditUsed()));
    }

    private static FitAnalysisGenerationSource fitSource() {
        FitAnalysisGenerationSource source = new FitAnalysisGenerationSource();
        source.setJobAnalysisId(60L);
        source.setJobPostingId(20L);
        source.setJobPostingRevision(2);
        source.setCompanyName("Acme");
        source.setJobTitle("Backend Engineer");
        source.setRequiredSkills("[\"Java\",\"Spring Boot\",\"MySQL\",\"Docker\"]");
        source.setPreferredSkills("[\"React\",\"TypeScript\"]");
        source.setDuties("Build APIs and operate production services.");
        source.setProfileSkills("[\"Java\",\"Docker\"]");
        source.setProfileCertificates("[]");
        source.setDesiredJob("Backend Engineer");
        return source;
    }
}
