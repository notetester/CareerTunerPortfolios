package com.careertuner.admin.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.settings.dto.AdminJobPostingFallbackSettingRequest;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.AuthUser;
import com.careertuner.jobposting.service.JobPostingFallbackPolicy;
import com.careertuner.jobposting.service.JobPostingFallbackPolicy.FallbackSettingSnapshot;

class AdminAiSettingsServiceTest {

    @Test
    void adminCanUpdateJobPostingFallbackSetting() {
        JobPostingFallbackPolicy policy = mock(JobPostingFallbackPolicy.class);
        AdminAiSettingsService service = new AdminAiSettingsService(policy);
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
        AdminAiSettingsService service = new AdminAiSettingsService(mock(JobPostingFallbackPolicy.class));

        assertThatThrownBy(() -> service.jobPostingFallback(new AuthUser(1L, "user@example.com", "USER")))
                .isInstanceOf(BusinessException.class);
    }
}
