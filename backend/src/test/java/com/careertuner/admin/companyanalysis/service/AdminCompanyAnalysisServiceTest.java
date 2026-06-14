package com.careertuner.admin.companyanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisMetadataRequest;
import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisSearchCriteria;
import com.careertuner.admin.companyanalysis.mapper.AdminCompanyAnalysisMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;

class AdminCompanyAnalysisServiceTest {

    @Test
    void companyAnalysesNormalizesCriteriaBeforeMapperCall() {
        AdminCompanyAnalysisMapper mapper = mock(AdminCompanyAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminCompanyAnalysisService service = new AdminCompanyAnalysisService(mapper, companyAnalysisMapper);
        when(mapper.findCompanyAnalyses(any())).thenReturn(List.of());

        service.companyAnalyses(admin(), AdminCompanyAnalysisSearchCriteria.builder()
                .keyword(" naver ")
                .sourceType("job-posting")
                .industry(" fintech ")
                .confirmed(false)
                .hasMemo(true)
                .checked(true)
                .refreshDue(false)
                .applicationCaseId(10L)
                .userId(20L)
                .createdFrom(LocalDate.of(2026, 6, 1))
                .createdTo(LocalDate.of(2026, 6, 30))
                .sort("refreshRecommendedAt.asc")
                .limit(500)
                .offset(-2)
                .build());

        ArgumentCaptor<AdminCompanyAnalysisSearchCriteria> captor =
                ArgumentCaptor.forClass(AdminCompanyAnalysisSearchCriteria.class);
        verify(mapper).findCompanyAnalyses(captor.capture());
        AdminCompanyAnalysisSearchCriteria criteria = captor.getValue();
        assertThat(criteria.keyword()).isEqualTo("naver");
        assertThat(criteria.sourceType()).isEqualTo("JOB_POSTING");
        assertThat(criteria.industry()).isEqualTo("fintech");
        assertThat(criteria.sort()).isEqualTo("REFRESH_RECOMMENDED_AT_ASC");
        assertThat(criteria.limit()).isEqualTo(200);
        assertThat(criteria.offset()).isZero();
        assertThat(criteria.confirmed()).isFalse();
        assertThat(criteria.hasMemo()).isTrue();
    }

    @Test
    void companyAnalysesRejectsInvalidSourceType() {
        AdminCompanyAnalysisMapper mapper = mock(AdminCompanyAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminCompanyAnalysisService service = new AdminCompanyAnalysisService(mapper, companyAnalysisMapper);

        assertThatThrownBy(() -> service.companyAnalyses(admin(), AdminCompanyAnalysisSearchCriteria.builder()
                .sourceType("unknown")
                .build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(mapper, never()).findCompanyAnalyses(any());
    }

    @Test
    void updateMetadataRequiresAdminRole() {
        AdminCompanyAnalysisMapper mapper = mock(AdminCompanyAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminCompanyAnalysisService service = new AdminCompanyAnalysisService(mapper, companyAnalysisMapper);
        AdminCompanyAnalysisMetadataRequest request = new AdminCompanyAnalysisMetadataRequest(
                "WEB",
                LocalDateTime.of(2026, 6, 10, 9, 0),
                LocalDateTime.of(2026, 6, 17, 9, 0),
                false,
                false);

        assertThatThrownBy(() -> service.updateMetadata(user(), 20L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        verify(mapper, never()).updateMetadata(any(), any(), any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void updateMetadataTrimsSourceTypeAndUpdatesMetadataFields() {
        AdminCompanyAnalysisMapper mapper = mock(AdminCompanyAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminCompanyAnalysisService service = new AdminCompanyAnalysisService(mapper, companyAnalysisMapper);
        LocalDateTime checkedAt = LocalDateTime.of(2026, 6, 10, 9, 0);
        LocalDateTime refreshRecommendedAt = LocalDateTime.of(2026, 6, 17, 9, 0);
        AdminCompanyAnalysisMetadataRequest request = new AdminCompanyAnalysisMetadataRequest(
                " WEB ",
                checkedAt,
                refreshRecommendedAt,
                false,
                false);

        when(mapper.updateMetadata(20L, "WEB", checkedAt, refreshRecommendedAt, false, false)).thenReturn(1);

        service.updateMetadata(admin(), 20L, request);

        verify(mapper).updateMetadata(20L, "WEB", checkedAt, refreshRecommendedAt, false, false);
    }

    @Test
    void updateMetadataAllowsPartialRequestWithoutForcingBlankDates() {
        AdminCompanyAnalysisMapper mapper = mock(AdminCompanyAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminCompanyAnalysisService service = new AdminCompanyAnalysisService(mapper, companyAnalysisMapper);
        AdminCompanyAnalysisMetadataRequest request = new AdminCompanyAnalysisMetadataRequest("WEB", null, null, false, false);

        when(mapper.updateMetadata(20L, "WEB", null, null, false, false)).thenReturn(1);

        service.updateMetadata(admin(), 20L, request);

        verify(mapper).updateMetadata(20L, "WEB", null, null, false, false);
    }

    @Test
    void updateMetadataClearsDateMetadataWhenRequested() {
        AdminCompanyAnalysisMapper mapper = mock(AdminCompanyAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminCompanyAnalysisService service = new AdminCompanyAnalysisService(mapper, companyAnalysisMapper);
        AdminCompanyAnalysisMetadataRequest request = new AdminCompanyAnalysisMetadataRequest("WEB", null, null, true, true);

        when(mapper.updateMetadata(20L, "WEB", null, null, true, true)).thenReturn(1);

        service.updateMetadata(admin(), 20L, request);

        verify(mapper).updateMetadata(20L, "WEB", null, null, true, true);
    }

    @Test
    void updateMetadataRejectsBlankSourceType() {
        AdminCompanyAnalysisMapper mapper = mock(AdminCompanyAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminCompanyAnalysisService service = new AdminCompanyAnalysisService(mapper, companyAnalysisMapper);
        AdminCompanyAnalysisMetadataRequest request = new AdminCompanyAnalysisMetadataRequest(" ", null, null, false, false);

        assertThatThrownBy(() -> service.updateMetadata(admin(), 20L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(mapper, never()).updateMetadata(any(), any(), any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void updateMetadataMapperPreservesOmittedDateMetadata() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/admin/companyanalysis/AdminCompanyAnalysisMapper.xml"));

        assertThat(xml)
                .contains("WHEN #{clearCheckedAt} THEN NULL")
                .contains("WHEN #{clearRefreshRecommendedAt} THEN NULL");
    }

    @Test
    void updateMetadataThrowsNotFoundWhenNoAnalysisRowUpdated() {
        AdminCompanyAnalysisMapper mapper = mock(AdminCompanyAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminCompanyAnalysisService service = new AdminCompanyAnalysisService(mapper, companyAnalysisMapper);
        AdminCompanyAnalysisMetadataRequest request = new AdminCompanyAnalysisMetadataRequest(
                "WEB",
                null,
                null,
                false,
                false);

        when(mapper.updateMetadata(eq(20L), eq("WEB"), isNull(), isNull(), eq(false), eq(false))).thenReturn(0);

        assertThatThrownBy(() -> service.updateMetadata(admin(), 20L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    private static AuthUser admin() {
        return new AuthUser(1L, "admin@example.com", "ADMIN");
    }

    private static AuthUser user() {
        return new AuthUser(2L, "user@example.com", "USER");
    }
}
