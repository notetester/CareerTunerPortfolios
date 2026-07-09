package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.mapper.JobPostingMapper;

/**
 * 사용자 공고 리비전 응답의 created_at 이 KST 로 보정되는지 검증한다.
 * DB(UTC) 저장 시각 2026-07-08T15:34:44 → 응답 2026-07-09T00:34:44(+9h).
 */
class JobPostingServiceTimezoneTest {

    @Test
    void getJobPostingRevisionsConvertsUtcCreatedAtToKst() {
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        ApplicationCaseMapper applicationCaseMapper = mock(ApplicationCaseMapper.class);
        when(applicationCaseMapper.findApplicationCaseByIdAndUserId(10L, 1L))
                .thenReturn(ApplicationCase.builder().id(10L).userId(1L)
                        .companyName("Test Company").jobTitle("Backend Developer").build());
        when(jobPostingMapper.findJobPostingRevisionsByCaseId(10L)).thenReturn(List.of(
                JobPosting.builder().id(1L).applicationCaseId(10L).revision(1)
                        .createdAt(LocalDateTime.of(2026, 7, 8, 15, 34, 44)).build()));

        JobPostingService service = new JobPostingService(
                new ApplicationCaseAccessService(applicationCaseMapper, jobPostingMapper),
                jobPostingMapper,
                mock(AiUsageLogService.class),
                mock(JobPostingFileStorage.class),
                mock(JobPostingTextExtractor.class));

        List<JobPostingResponse> revisions = service.getJobPostingRevisions(1L, 10L);

        assertThat(revisions).hasSize(1);
        assertThat(revisions.get(0).createdAt()).isEqualTo(LocalDateTime.of(2026, 7, 9, 0, 34, 44));
    }
}
