package com.careertuner.admin.settings.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.settings.dto.AdminJobPostingFallbackSettingRequest;
import com.careertuner.admin.settings.dto.AdminJobPostingFallbackSettingResponse;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.jobposting.service.JobPostingFallbackPolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAiSettingsService {

    private final JobPostingFallbackPolicy fallbackPolicy;

    @Transactional(readOnly = true)
    public AdminJobPostingFallbackSettingResponse jobPostingFallback(AuthUser authUser) {
        requireAdmin(authUser);
        return AdminJobPostingFallbackSettingResponse.from(fallbackPolicy.current());
    }

    @Transactional
    public AdminJobPostingFallbackSettingResponse updateJobPostingFallback(
            AuthUser authUser,
            AdminJobPostingFallbackSettingRequest request) {
        requireAdmin(authUser);
        if (request == null || request.enabled() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "enabled is required.");
        }
        return AdminJobPostingFallbackSettingResponse.from(fallbackPolicy.configure(
                request.enabled(),
                request.allowedStages(),
                authUser.id()));
    }

    private static void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Admin role is required.");
        }
    }
}
