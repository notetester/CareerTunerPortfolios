package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.dto.AiUsageFailureResponse;
import com.careertuner.applicationcase.dto.AnalysisResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.companyanalysis.service.CompanyAnalysisService;
import com.careertuner.jobanalysis.domain.JobAnalysis;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;
import com.careertuner.jobanalysis.service.JobAnalysisService;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.notification.mapper.NotificationMapper;

/**
 * /application-cases/{id}/analysis 응답의 직접 {@code from} 경로도 시각을 KST 로 보정하는지 검증한다.
 * DB(UTC) 저장 시각 2026-07-08T15:34:44 → 응답 2026-07-09T00:34:44(+9h).
 */
class ApplicationCaseServiceImplAnalysisTimezoneTest {

    @Test
    void getAnalysisConvertsJobAnalysisCreatedAtToKst() {
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        ApplicationCaseAccessService accessService = mock(ApplicationCaseAccessService.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);

        when(accessService.requireOwned(1L, 10L))
                .thenReturn(ApplicationCase.builder().id(10L).userId(1L).build());
        when(jobAnalysisMapper.findLatestJobAnalysisByCaseId(10L)).thenReturn(
                JobAnalysis.builder().id(5L).applicationCaseId(10L)
                        .createdAt(LocalDateTime.of(2026, 7, 8, 15, 34, 44)).build());
        when(applicationCaseMapper.findLatestFitAnalysisByCaseId(10L)).thenReturn(null);

        ApplicationCaseServiceImpl service = new ApplicationCaseServiceImpl(
                applicationCaseMapper,
                mock(ApplicationCaseExtractionMapper.class),
                accessService,
                mock(JobPostingService.class),
                mock(JobAnalysisService.class),
                mock(CompanyAnalysisService.class),
                jobAnalysisMapper,
                mock(OpenAiResponsesClient.class),
                mock(NotificationMapper.class),
                mock(ApplicationCaseAutoPipelineService.class));

        AnalysisResponse response = service.getAnalysis(1L, 10L);

        assertThat(response.jobAnalysis().createdAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 9, 0, 34, 44));
    }

    @Test
    void getConvertsDbTimestampsToKstAndKeepsArchivedAt() {
        // created_at/updated_at/deleted_at 은 DB(UTC) → +9h. archived_at 은 Java(now)로 이미 KST → 그대로.
        LocalDateTime dbUtc = LocalDateTime.of(2026, 7, 8, 15, 34, 44);
        LocalDateTime archivedKst = LocalDateTime.of(2026, 7, 9, 10, 0, 0);

        ApplicationCaseAccessService accessService = mock(ApplicationCaseAccessService.class);
        when(accessService.requireOwned(1L, 10L)).thenReturn(ApplicationCase.builder()
                .id(10L).userId(1L)
                .createdAt(dbUtc).updatedAt(dbUtc).deletedAt(dbUtc).archivedAt(archivedKst)
                .build());

        ApplicationCaseServiceImpl service = new ApplicationCaseServiceImpl(
                mock(ApplicationCaseMapper.class),
                mock(ApplicationCaseExtractionMapper.class),
                accessService,
                mock(JobPostingService.class),
                mock(JobAnalysisService.class),
                mock(CompanyAnalysisService.class),
                mock(JobAnalysisMapper.class),
                mock(OpenAiResponsesClient.class),
                mock(NotificationMapper.class),
                mock(ApplicationCaseAutoPipelineService.class));

        ApplicationCaseResponse response = service.get(1L, 10L);

        LocalDateTime expectedKst = LocalDateTime.of(2026, 7, 9, 0, 34, 44);
        assertThat(response.createdAt()).isEqualTo(expectedKst);
        assertThat(response.updatedAt()).isEqualTo(expectedKst);
        assertThat(response.deletedAt()).isEqualTo(expectedKst);
        assertThat(response.archivedAt()).isEqualTo(archivedKst);
    }

    @Test
    void getAiUsageFailuresConvertsCreatedAtToKst() {
        // 실패 로그 created_at 도 DB(UTC) → 화면(KST) +9h (AnalysisFailureNotice 표시용).
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        ApplicationCaseAccessService accessService = mock(ApplicationCaseAccessService.class);
        when(applicationCaseMapper.findBFailureLogsByCaseId(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.List.of(new AiUsageFailureResponse(
                        "JOB_ANALYSIS", "timeout", LocalDateTime.of(2026, 7, 8, 15, 34, 44))));

        ApplicationCaseServiceImpl service = new ApplicationCaseServiceImpl(
                applicationCaseMapper,
                mock(ApplicationCaseExtractionMapper.class),
                accessService,
                mock(JobPostingService.class),
                mock(JobAnalysisService.class),
                mock(CompanyAnalysisService.class),
                mock(JobAnalysisMapper.class),
                mock(OpenAiResponsesClient.class),
                mock(NotificationMapper.class),
                mock(ApplicationCaseAutoPipelineService.class));

        java.util.List<AiUsageFailureResponse> failures = service.getAiUsageFailures(1L, 10L, 20);

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).createdAt()).isEqualTo(LocalDateTime.of(2026, 7, 9, 0, 34, 44));
        assertThat(failures.get(0).featureType()).isEqualTo("JOB_ANALYSIS");
    }
}
