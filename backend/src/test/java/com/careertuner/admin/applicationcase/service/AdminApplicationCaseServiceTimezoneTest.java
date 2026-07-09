package com.careertuner.admin.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.aiusage.mapper.AdminAiUsageMapper;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseDetail;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseRow;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseSearchCriteria;
import com.careertuner.admin.applicationcase.mapper.AdminApplicationCaseMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.security.AuthUser;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer;
import com.careertuner.jobanalysis.domain.JobAnalysis;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.mapper.JobPostingMapper;

/**
 * 관리자 지원 건 상세도 사용자 화면과 동일하게 시각을 KST 로 보정하는지 검증한다.
 * DB(UTC) 저장 시각 2026-07-08T15:34:44 → 응답 2026-07-09T00:34:44(+9h).
 */
class AdminApplicationCaseServiceTimezoneTest {

    private static final LocalDateTime DB_UTC = LocalDateTime.of(2026, 7, 8, 15, 34, 44);
    private static final LocalDateTime EXPECTED_KST = LocalDateTime.of(2026, 7, 9, 0, 34, 44);

    @Test
    void detailConvertsCreatedAtOfRevisionsAndAnalysesToKst() {
        AdminApplicationCaseMapper adminMapper = mock(AdminApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminAiUsageMapper aiUsageMapper = mock(AdminAiUsageMapper.class);

        AdminApplicationCaseRow row = new AdminApplicationCaseRow();
        row.setId(7L);
        when(adminMapper.findApplicationCase(7L)).thenReturn(row);
        when(jobPostingMapper.findJobPostingRevisionsByCaseId(7L)).thenReturn(List.of(
                JobPosting.builder().id(1L).applicationCaseId(7L).revision(1).createdAt(DB_UTC).build()));
        when(jobAnalysisMapper.findJobAnalysisHistoryByCaseId(7L)).thenReturn(List.of(
                JobAnalysis.builder().id(2L).applicationCaseId(7L).createdAt(DB_UTC).build()));
        when(companyAnalysisMapper.findCompanyAnalysisHistoryByCaseId(7L)).thenReturn(List.of(
                CompanyAnalysis.builder().id(3L).applicationCaseId(7L).createdAt(DB_UTC).build()));
        when(aiUsageMapper.findBUsageLogsByCaseId(7L, 100)).thenReturn(List.of());

        AdminApplicationCaseService service = new AdminApplicationCaseService(
                adminMapper,
                mock(ApplicationCaseMapper.class),
                jobPostingMapper,
                jobAnalysisMapper,
                companyAnalysisMapper,
                aiUsageMapper,
                mock(BCompanyAnalysisCanonicalizer.class));

        AdminApplicationCaseDetail detail = service.detail(admin(), 7L);

        assertThat(detail.jobPostings().get(0).createdAt()).isEqualTo(EXPECTED_KST);
        assertThat(detail.jobAnalyses().get(0).createdAt()).isEqualTo(EXPECTED_KST);
        assertThat(detail.companyAnalyses().get(0).createdAt()).isEqualTo(EXPECTED_KST);
    }

    @Test
    void detailConvertsCaseHeaderTimesToKstAndKeepsArchivedAt() {
        // 케이스 헤더: created_at/updated_at/deleted_at 과 최신 분석 시각(MAX(created_at))은 DB(UTC) → +9h.
        // archived_at 은 Java(now)로 이미 KST → 그대로.
        LocalDateTime archivedKst = LocalDateTime.of(2026, 7, 9, 10, 0, 0);
        AdminApplicationCaseMapper adminMapper = mock(AdminApplicationCaseMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminAiUsageMapper aiUsageMapper = mock(AdminAiUsageMapper.class);

        AdminApplicationCaseRow row = new AdminApplicationCaseRow();
        row.setId(7L);
        row.setCreatedAt(DB_UTC);
        row.setUpdatedAt(DB_UTC);
        row.setDeletedAt(DB_UTC);
        row.setLatestJobAnalysisAt(DB_UTC);
        row.setLatestCompanyAnalysisAt(DB_UTC);
        row.setArchivedAt(archivedKst);
        when(adminMapper.findApplicationCase(7L)).thenReturn(row);
        when(jobPostingMapper.findJobPostingRevisionsByCaseId(7L)).thenReturn(List.of());
        when(jobAnalysisMapper.findJobAnalysisHistoryByCaseId(7L)).thenReturn(List.of());
        when(companyAnalysisMapper.findCompanyAnalysisHistoryByCaseId(7L)).thenReturn(List.of());
        when(aiUsageMapper.findBUsageLogsByCaseId(7L, 100)).thenReturn(List.of());

        AdminApplicationCaseService service = new AdminApplicationCaseService(
                adminMapper,
                mock(ApplicationCaseMapper.class),
                jobPostingMapper,
                jobAnalysisMapper,
                companyAnalysisMapper,
                aiUsageMapper,
                mock(BCompanyAnalysisCanonicalizer.class));

        AdminApplicationCaseRow header = service.detail(admin(), 7L).applicationCase();

        assertThat(header.getCreatedAt()).isEqualTo(EXPECTED_KST);
        assertThat(header.getUpdatedAt()).isEqualTo(EXPECTED_KST);
        assertThat(header.getDeletedAt()).isEqualTo(EXPECTED_KST);
        assertThat(header.getLatestJobAnalysisAt()).isEqualTo(EXPECTED_KST);
        assertThat(header.getLatestCompanyAnalysisAt()).isEqualTo(EXPECTED_KST);
        assertThat(header.getArchivedAt()).isEqualTo(archivedKst);
    }

    @Test
    void applicationCasesConvertsRowCreatedAtToKst() {
        AdminApplicationCaseMapper adminMapper = mock(AdminApplicationCaseMapper.class);
        AdminApplicationCaseRow row = new AdminApplicationCaseRow();
        row.setId(7L);
        row.setCreatedAt(DB_UTC);
        when(adminMapper.findApplicationCases(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(row));

        AdminApplicationCaseService service = new AdminApplicationCaseService(
                adminMapper,
                mock(ApplicationCaseMapper.class),
                mock(JobPostingMapper.class),
                mock(JobAnalysisMapper.class),
                mock(CompanyAnalysisMapper.class),
                mock(AdminAiUsageMapper.class),
                mock(BCompanyAnalysisCanonicalizer.class));

        List<AdminApplicationCaseRow> rows =
                service.applicationCases(admin(), AdminApplicationCaseSearchCriteria.builder().build());

        assertThat(rows.get(0).getCreatedAt()).isEqualTo(EXPECTED_KST);
    }

    private static AuthUser admin() {
        return new AuthUser(1L, "admin@example.com", "ADMIN");
    }
}
