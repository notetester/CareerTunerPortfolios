package com.careertuner.admin.companyanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisMetadataRequest;
import com.careertuner.admin.companyanalysis.mapper.AdminCompanyAnalysisMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;

class AdminCompanyAnalysisServiceTest {

    @Test
    void updateMetadataRequiresAdminRole() {
        AdminCompanyAnalysisMapper mapper = mock(AdminCompanyAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminCompanyAnalysisService service = new AdminCompanyAnalysisService(mapper, companyAnalysisMapper);
        AdminCompanyAnalysisMetadataRequest request = new AdminCompanyAnalysisMetadataRequest(
                "WEB",
                LocalDateTime.of(2026, 6, 10, 9, 0),
                LocalDateTime.of(2026, 6, 17, 9, 0));

        assertThatThrownBy(() -> service.updateMetadata(user(), 20L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        verify(mapper, never()).updateMetadata(any(), any(), any(), any());
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
                refreshRecommendedAt);

        when(mapper.updateMetadata(20L, "WEB", checkedAt, refreshRecommendedAt)).thenReturn(1);

        service.updateMetadata(admin(), 20L, request);

        verify(mapper).updateMetadata(20L, "WEB", checkedAt, refreshRecommendedAt);
    }

    @Test
    void updateMetadataAllowsPartialRequestWithoutForcingBlankDates() {
        AdminCompanyAnalysisMapper mapper = mock(AdminCompanyAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminCompanyAnalysisService service = new AdminCompanyAnalysisService(mapper, companyAnalysisMapper);
        AdminCompanyAnalysisMetadataRequest request = new AdminCompanyAnalysisMetadataRequest("WEB", null, null);

        when(mapper.updateMetadata(20L, "WEB", null, null)).thenReturn(1);

        service.updateMetadata(admin(), 20L, request);

        verify(mapper).updateMetadata(20L, "WEB", null, null);
    }

    @Test
    void updateMetadataMapperPreservesOmittedDateMetadata() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/admin/companyanalysis/AdminCompanyAnalysisMapper.xml"));

        assertThat(xml)
                .contains("checked_at = COALESCE(#{checkedAt}, checked_at)")
                .contains("refresh_recommended_at = COALESCE(#{refreshRecommendedAt}, refresh_recommended_at)");
    }

    @Test
    void updateMetadataThrowsNotFoundWhenNoAnalysisRowUpdated() {
        AdminCompanyAnalysisMapper mapper = mock(AdminCompanyAnalysisMapper.class);
        CompanyAnalysisMapper companyAnalysisMapper = mock(CompanyAnalysisMapper.class);
        AdminCompanyAnalysisService service = new AdminCompanyAnalysisService(mapper, companyAnalysisMapper);
        AdminCompanyAnalysisMetadataRequest request = new AdminCompanyAnalysisMetadataRequest(
                "WEB",
                null,
                null);

        when(mapper.updateMetadata(eq(20L), eq("WEB"), any(), any())).thenReturn(0);

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
