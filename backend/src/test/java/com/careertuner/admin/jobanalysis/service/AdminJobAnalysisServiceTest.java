package com.careertuner.admin.jobanalysis.service;

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

import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisSearchCriteria;
import com.careertuner.admin.jobanalysis.mapper.AdminJobAnalysisMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;

class AdminJobAnalysisServiceTest {

    @Test
    void jobAnalysesNormalizesCriteriaBeforeMapperCall() {
        AdminJobAnalysisMapper mapper = mock(AdminJobAnalysisMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        AdminJobAnalysisService service = new AdminJobAnalysisService(mapper, jobAnalysisMapper);
        when(mapper.findJobAnalyses(any())).thenReturn(List.of());

        service.jobAnalyses(admin(), AdminJobAnalysisSearchCriteria.builder()
                .keyword(" java ")
                .difficulty("normal")
                .confirmed(true)
                .hasMemo(false)
                .applicationCaseId(10L)
                .userId(20L)
                .createdFrom(LocalDate.of(2026, 6, 1))
                .createdTo(LocalDate.of(2026, 6, 30))
                .sort("-confirmedAt")
                .limit(0)
                .offset(-3)
                .build());

        ArgumentCaptor<AdminJobAnalysisSearchCriteria> captor =
                ArgumentCaptor.forClass(AdminJobAnalysisSearchCriteria.class);
        verify(mapper).findJobAnalyses(captor.capture());
        AdminJobAnalysisSearchCriteria criteria = captor.getValue();
        assertThat(criteria.keyword()).isEqualTo("java");
        assertThat(criteria.difficulty()).isEqualTo("NORMAL");
        assertThat(criteria.sort()).isEqualTo("CONFIRMED_AT_DESC");
        assertThat(criteria.limit()).isEqualTo(50);
        assertThat(criteria.offset()).isZero();
        assertThat(criteria.confirmed()).isTrue();
        assertThat(criteria.hasMemo()).isFalse();
    }

    @Test
    void jobAnalysesRejectsInvalidDifficulty() {
        AdminJobAnalysisMapper mapper = mock(AdminJobAnalysisMapper.class);
        JobAnalysisMapper jobAnalysisMapper = mock(JobAnalysisMapper.class);
        AdminJobAnalysisService service = new AdminJobAnalysisService(mapper, jobAnalysisMapper);

        assertThatThrownBy(() -> service.jobAnalyses(admin(), AdminJobAnalysisSearchCriteria.builder()
                .difficulty("banana")
                .build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(mapper, never()).findJobAnalyses(any());
    }

    private static AuthUser admin() {
        return new AuthUser(1L, "admin@example.com", "ADMIN");
    }
}
