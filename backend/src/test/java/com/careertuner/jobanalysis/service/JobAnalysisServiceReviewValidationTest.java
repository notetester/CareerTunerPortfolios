package com.careertuner.jobanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.careertuner.jobanalysis.domain.JobAnalysis;
import com.careertuner.jobanalysis.dto.JobAnalysisReviewRequest;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;
import com.careertuner.jobposting.mapper.JobPostingMapper;

import tools.jackson.databind.ObjectMapper;

class JobAnalysisServiceReviewValidationTest {

    @Test
    void reviewJobAnalysisRejectsEvidenceStringArray() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        JobAnalysisService service = jobAnalysisService(applicationCaseMapper, jobPostingMapper, jobAnalysisMapper);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase());
        when(jobAnalysisMapper.findJobAnalysisByIdAndCaseId(20L, 10L)).thenReturn(existingAnalysis());

        JobAnalysisReviewRequest request = new JobAnalysisReviewRequest(
                null, null, null, null, null, null, null, null,
                "[\"requiredSkills\"]", null, null);

        assertThatThrownBy(() -> service.reviewJobAnalysis(1L, 10L, 20L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT))
                .hasMessageContaining("JSON");

        verify(jobAnalysisMapper, never()).updateJobAnalysisReview(any(JobAnalysis.class));
    }

    @Test
    void reviewJobAnalysisAcceptsEvidenceObjectArray() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        JobAnalysisService service = jobAnalysisService(applicationCaseMapper, jobPostingMapper, jobAnalysisMapper);
        String evidence = "[{\"field\":\"requiredSkills\",\"quote\":\"Java\"}]";

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase());
        when(jobAnalysisMapper.findJobAnalysisByIdAndCaseId(20L, 10L))
                .thenReturn(existingAnalysis())
                .thenReturn(existingAnalysisWithEvidence(evidence));

        JobAnalysisReviewRequest request = new JobAnalysisReviewRequest(
                null, null, null, null, null, null, null, null,
                evidence, null, null);

        JobAnalysisResponse response = service.reviewJobAnalysis(1L, 10L, 20L, request);

        ArgumentCaptor<JobAnalysis> analysisCaptor = ArgumentCaptor.forClass(JobAnalysis.class);
        verify(jobAnalysisMapper).updateJobAnalysisReview(analysisCaptor.capture());
        assertThat(analysisCaptor.getValue().getEvidence()).isEqualTo(evidence);
        assertThat(response.evidence()).isEqualTo(evidence);
    }

    @Test
    void reviewJobAnalysisRejectsAmbiguousConditionsStringArray() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        JobAnalysisService service = jobAnalysisService(applicationCaseMapper, jobPostingMapper, jobAnalysisMapper);

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase());
        when(jobAnalysisMapper.findJobAnalysisByIdAndCaseId(20L, 10L)).thenReturn(existingAnalysis());

        JobAnalysisReviewRequest request = new JobAnalysisReviewRequest(
                null, null, null, null, null, null, null, null,
                null, "[\"experience is not explicit\"]", null);

        assertThatThrownBy(() -> service.reviewJobAnalysis(1L, 10L, 20L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT))
                .hasMessageContaining("JSON");

        verify(jobAnalysisMapper, never()).updateJobAnalysisReview(any(JobAnalysis.class));
    }

    @Test
    void reviewJobAnalysisAcceptsAmbiguousConditionsObjectArray() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        JobAnalysisService service = jobAnalysisService(applicationCaseMapper, jobPostingMapper, jobAnalysisMapper);
        String ambiguousConditions = "[{\"condition\":\"experience is not explicit\",\"assumption\":\"junior\"}]";

        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L)).thenReturn(applicationCase());
        when(jobAnalysisMapper.findJobAnalysisByIdAndCaseId(20L, 10L))
                .thenReturn(existingAnalysis())
                .thenReturn(existingAnalysisWithAmbiguousConditions(ambiguousConditions));

        JobAnalysisReviewRequest request = new JobAnalysisReviewRequest(
                null, null, null, null, null, null, null, null,
                null, ambiguousConditions, null);

        JobAnalysisResponse response = service.reviewJobAnalysis(1L, 10L, 20L, request);

        ArgumentCaptor<JobAnalysis> analysisCaptor = ArgumentCaptor.forClass(JobAnalysis.class);
        verify(jobAnalysisMapper).updateJobAnalysisReview(analysisCaptor.capture());
        assertThat(analysisCaptor.getValue().getAmbiguousConditions()).isEqualTo(ambiguousConditions);
        assertThat(response.ambiguousConditions()).isEqualTo(ambiguousConditions);
    }

    private static JobAnalysisService jobAnalysisService(ApplicationCaseMapper applicationCaseMapper,
                                                         JobPostingMapper jobPostingMapper,
                                                         JobAnalysisMapper jobAnalysisMapper) {
        ApplicationCaseAccessService accessService = new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper);
        return new JobAnalysisService(
                accessService,
                jobAnalysisMapper,
                mock(BAnalysisGenerationService.class),
                mock(AiUsageLogService.class),
                mock(ApplicationCaseAnalysisStatusService.class),
                mock(TransactionTemplate.class),
                new BAnalysisJsonValidator(new ObjectMapper()));
    }

    private static ApplicationCase applicationCase() {
        return ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Test Company")
                .jobTitle("Backend Developer")
                .build();
    }

    private static JobAnalysis existingAnalysis() {
        return existingAnalysisWithEvidence("[]");
    }

    private static JobAnalysis existingAnalysisWithEvidence(String evidence) {
        return existingAnalysisWithEvidenceAndAmbiguousConditions(evidence, "[]");
    }

    private static JobAnalysis existingAnalysisWithAmbiguousConditions(String ambiguousConditions) {
        return existingAnalysisWithEvidenceAndAmbiguousConditions("[]", ambiguousConditions);
    }

    private static JobAnalysis existingAnalysisWithEvidenceAndAmbiguousConditions(String evidence, String ambiguousConditions) {
        return JobAnalysis.builder()
                .id(20L)
                .applicationCaseId(10L)
                .jobPostingId(30L)
                .jobPostingRevision(2)
                .employmentType("Full-time")
                .experienceLevel("Junior")
                .requiredSkills("[\"Java\"]")
                .preferredSkills("[]")
                .duties("Build services")
                .qualifications("Java")
                .difficulty("NORMAL")
                .summary("Summary")
                .evidence(evidence)
                .ambiguousConditions(ambiguousConditions)
                .build();
    }
}
