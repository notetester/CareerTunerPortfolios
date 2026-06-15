package com.careertuner.admin.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.admin.aiusage.mapper.AdminAiUsageMapper;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseSearchCriteria;
import com.careertuner.admin.applicationcase.mapper.AdminApplicationCaseMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;
import com.careertuner.jobposting.mapper.JobPostingMapper;

class AdminApplicationCaseServiceTest {

    @Test
    void applicationCasesNormalizesCriteriaBeforeMapperCall() {
        AdminApplicationCaseMapper mapper = mock(AdminApplicationCaseMapper.class);
        AdminApplicationCaseService service = service(mapper);
        when(mapper.findApplicationCases(any())).thenReturn(List.of());

        service.applicationCases(admin(), AdminApplicationCaseSearchCriteria.builder()
                .keyword(" acme ")
                .status("ready")
                .includeArchived(false)
                .includeDeleted(true)
                .sourceType("text")
                .favorite(true)
                .createdFrom(LocalDate.of(2026, 6, 1))
                .createdTo(LocalDate.of(2026, 6, 10))
                .deadlineFrom(LocalDate.of(2026, 7, 1))
                .deadlineTo(LocalDate.of(2026, 7, 31))
                .analysisState("complete")
                .sort("-deadlineDate")
                .limit(500)
                .offset(-10)
                .build());

        ArgumentCaptor<AdminApplicationCaseSearchCriteria> captor =
                ArgumentCaptor.forClass(AdminApplicationCaseSearchCriteria.class);
        verify(mapper).findApplicationCases(captor.capture());
        AdminApplicationCaseSearchCriteria criteria = captor.getValue();
        assertThat(criteria.keyword()).isEqualTo("acme");
        assertThat(criteria.status()).isEqualTo("READY");
        assertThat(criteria.sourceType()).isEqualTo("TEXT");
        assertThat(criteria.analysisState()).isEqualTo("COMPLETE_ANALYSIS");
        assertThat(criteria.sort()).isEqualTo("DEADLINE_DATE_DESC");
        assertThat(criteria.limit()).isEqualTo(200);
        assertThat(criteria.offset()).isZero();
        assertThat(criteria.includeArchived()).isFalse();
        assertThat(criteria.includeDeleted()).isTrue();
    }

    @Test
    void applicationCasesNormalizesBlankFiltersAndDefaultPaging() {
        AdminApplicationCaseMapper mapper = mock(AdminApplicationCaseMapper.class);
        AdminApplicationCaseService service = service(mapper);
        when(mapper.findApplicationCases(any())).thenReturn(List.of());

        service.applicationCases(admin(), AdminApplicationCaseSearchCriteria.builder()
                .keyword("   ")
                .status(" ")
                .includeArchived(true)
                .sourceType("")
                .analysisState("\t")
                .sort(" ")
                .limit(0)
                .offset(-5)
                .build());

        ArgumentCaptor<AdminApplicationCaseSearchCriteria> captor =
                ArgumentCaptor.forClass(AdminApplicationCaseSearchCriteria.class);
        verify(mapper).findApplicationCases(captor.capture());
        AdminApplicationCaseSearchCriteria criteria = captor.getValue();
        assertThat(criteria.keyword()).isNull();
        assertThat(criteria.status()).isNull();
        assertThat(criteria.sourceType()).isNull();
        assertThat(criteria.analysisState()).isNull();
        assertThat(criteria.sort()).isEqualTo("UPDATED_AT_DESC");
        assertThat(criteria.limit()).isEqualTo(50);
        assertThat(criteria.offset()).isZero();
        assertThat(criteria.includeArchived()).isTrue();
    }

    @Test
    void applicationCasesRejectsEnumsOutsideAllowlists() {
        AdminApplicationCaseMapper mapper = mock(AdminApplicationCaseMapper.class);
        AdminApplicationCaseService service = service(mapper);

        assertThatThrownBy(() -> service.applicationCases(admin(), AdminApplicationCaseSearchCriteria.builder()
                .status("WAITING")
                .build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        assertThatThrownBy(() -> service.applicationCases(admin(), AdminApplicationCaseSearchCriteria.builder()
                .sourceType("HTML")
                .build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        assertThatThrownBy(() -> service.applicationCases(admin(), AdminApplicationCaseSearchCriteria.builder()
                .analysisState("PARTIAL")
                .build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(mapper, never()).findApplicationCases(any());
    }

    @Test
    void applicationCasesRejectsSortOutsideAllowlist() {
        AdminApplicationCaseMapper mapper = mock(AdminApplicationCaseMapper.class);
        AdminApplicationCaseService service = service(mapper);

        assertThatThrownBy(() -> service.applicationCases(admin(), AdminApplicationCaseSearchCriteria.builder()
                .sort("created_at;drop")
                .build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(mapper, never()).findApplicationCases(any());
    }

    private static AdminApplicationCaseService service(AdminApplicationCaseMapper mapper) {
        return new AdminApplicationCaseService(
                mapper,
                mock(ApplicationCaseMapper.class),
                mock(JobPostingMapper.class),
                mock(JobAnalysisMapper.class),
                mock(CompanyAnalysisMapper.class),
                mock(AdminAiUsageMapper.class));
    }

    private static AuthUser admin() {
        return new AuthUser(1L, "admin@example.com", "ADMIN");
    }
}
