package com.careertuner.admin.aiusage.service;

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

import com.careertuner.admin.aiusage.dto.AdminAiUsageSearchCriteria;
import com.careertuner.admin.aiusage.mapper.AdminAiUsageMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

class AdminAiUsageServiceTest {

    @Test
    void bUsageLogsNormalizesCriteriaBeforeMapperCall() {
        AdminAiUsageMapper mapper = mock(AdminAiUsageMapper.class);
        AdminAiUsageService service = new AdminAiUsageService(mapper);
        when(mapper.findBUsageLogs(any())).thenReturn(List.of());

        service.bUsageLogs(admin(), AdminAiUsageSearchCriteria.builder()
                .featureType("job-analysis")
                .status("failed")
                .keyword(" timeout ")
                .applicationCaseId(10L)
                .userId(20L)
                .model(" gpt-4.1-mini ")
                .createdFrom(LocalDate.of(2026, 6, 1))
                .createdTo(LocalDate.of(2026, 6, 30))
                .sort("tokenUsage.asc")
                .limit(250)
                .offset(-1)
                .build());

        ArgumentCaptor<AdminAiUsageSearchCriteria> captor =
                ArgumentCaptor.forClass(AdminAiUsageSearchCriteria.class);
        verify(mapper).findBUsageLogs(captor.capture());
        AdminAiUsageSearchCriteria criteria = captor.getValue();
        assertThat(criteria.featureType()).isEqualTo("JOB_ANALYSIS");
        assertThat(criteria.status()).isEqualTo("FAILED");
        assertThat(criteria.keyword()).isEqualTo("timeout");
        assertThat(criteria.model()).isEqualTo("gpt-4.1-mini");
        assertThat(criteria.sort()).isEqualTo("TOKEN_USAGE_ASC");
        assertThat(criteria.limit()).isEqualTo(200);
        assertThat(criteria.offset()).isZero();
    }

    @Test
    void bUsageLogsRejectsInvalidFeatureType() {
        AdminAiUsageMapper mapper = mock(AdminAiUsageMapper.class);
        AdminAiUsageService service = new AdminAiUsageService(mapper);

        assertThatThrownBy(() -> service.bUsageLogs(admin(), AdminAiUsageSearchCriteria.builder()
                .featureType("FIT_ANALYSIS")
                .build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(mapper, never()).findBUsageLogs(any());
    }

    @Test
    void bUsageLogsRejectsInvalidStatus() {
        AdminAiUsageMapper mapper = mock(AdminAiUsageMapper.class);
        AdminAiUsageService service = new AdminAiUsageService(mapper);

        assertThatThrownBy(() -> service.bUsageLogs(admin(), AdminAiUsageSearchCriteria.builder()
                .status("banana")
                .build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(mapper, never()).findBUsageLogs(any());
    }

    private static AuthUser admin() {
        return new AuthUser(1L, "admin@example.com", "ADMIN");
    }
}
