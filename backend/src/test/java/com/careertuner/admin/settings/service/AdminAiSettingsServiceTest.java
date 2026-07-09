package com.careertuner.admin.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.settings.dto.AdminJobPostingFallbackSettingRequest;
import com.careertuner.admin.settings.dto.AdminJobPostingUploadLimitSettingRequest;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.AuthUser;
import com.careertuner.jobposting.service.JobPostingFallbackPolicy;
import com.careertuner.jobposting.service.JobPostingFallbackPolicy.FallbackSettingSnapshot;
import com.careertuner.jobposting.service.JobPostingUploadLimitPolicy;
import com.careertuner.jobposting.service.JobPostingUploadLimitPolicy.UploadLimitSnapshot;

class AdminAiSettingsServiceTest {

    @Test
    void adminCanUpdateJobPostingFallbackSetting() {
        JobPostingFallbackPolicy policy = mock(JobPostingFallbackPolicy.class);
        AdminAiSettingsService service = new AdminAiSettingsService(policy, mock(JobPostingUploadLimitPolicy.class));
        AuthUser admin = new AuthUser(7L, "admin@example.com", "ADMIN");
        FallbackSettingSnapshot snapshot = new FallbackSettingSnapshot(
                true,
                List.of(JobPostingFallbackPolicy.STAGE_IMAGE_OCR),
                JobPostingFallbackPolicy.AVAILABLE_STAGES,
                "DATABASE");
        when(policy.configure(true, List.of(JobPostingFallbackPolicy.STAGE_IMAGE_OCR), 7L))
                .thenReturn(snapshot);

        var response = service.updateJobPostingFallback(
                admin,
                new AdminJobPostingFallbackSettingRequest(true, List.of(JobPostingFallbackPolicy.STAGE_IMAGE_OCR)));

        assertThat(response.enabled()).isTrue();
        assertThat(response.allowedStages()).containsExactly(JobPostingFallbackPolicy.STAGE_IMAGE_OCR);
        verify(policy).configure(true, List.of(JobPostingFallbackPolicy.STAGE_IMAGE_OCR), 7L);
    }

    @Test
    void nonAdminCannotReadJobPostingFallbackSetting() {
        AdminAiSettingsService service = new AdminAiSettingsService(
                mock(JobPostingFallbackPolicy.class), mock(JobPostingUploadLimitPolicy.class));

        assertThatThrownBy(() -> service.jobPostingFallback(new AuthUser(1L, "user@example.com", "USER")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void adminCanUpdateUploadLimitSetting() {
        JobPostingUploadLimitPolicy uploadPolicy = mock(JobPostingUploadLimitPolicy.class);
        AdminAiSettingsService service =
                new AdminAiSettingsService(mock(JobPostingFallbackPolicy.class), uploadPolicy);
        AuthUser admin = new AuthUser(7L, "admin@example.com", "ADMIN");
        UploadLimitSnapshot snapshot =
                new UploadLimitSnapshot(15L * 1024 * 1024, 1024 * 1024, 20L * 1024 * 1024, "DATABASE");
        when(uploadPolicy.configure(15L * 1024 * 1024, 7L)).thenReturn(snapshot);

        var response = service.updateJobPostingUploadLimit(
                admin, new AdminJobPostingUploadLimitSettingRequest(15L * 1024 * 1024));

        assertThat(response.maxBytes()).isEqualTo(15L * 1024 * 1024);
        verify(uploadPolicy).configure(15L * 1024 * 1024, 7L);
    }

    @Test
    void nonAdminCannotReadUploadLimitSetting() {
        AdminAiSettingsService service = new AdminAiSettingsService(
                mock(JobPostingFallbackPolicy.class), mock(JobPostingUploadLimitPolicy.class));

        assertThatThrownBy(() -> service.jobPostingUploadLimit(new AuthUser(1L, "user@example.com", "USER")))
                .isInstanceOf(BusinessException.class);
    }
}
