package com.careertuner.admin.settings.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.settings.dto.AdminJobPostingFallbackSettingRequest;
import com.careertuner.admin.settings.dto.AdminJobPostingFallbackSettingResponse;
import com.careertuner.admin.settings.dto.AdminJobPostingUploadLimitSettingRequest;
import com.careertuner.admin.settings.dto.AdminJobPostingUploadLimitSettingResponse;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.jobposting.service.JobPostingFallbackPolicy;
import com.careertuner.jobposting.service.JobPostingUploadLimitPolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAiSettingsService {

    private final JobPostingFallbackPolicy fallbackPolicy;
    private final JobPostingUploadLimitPolicy uploadLimitPolicy;

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

    @Transactional(readOnly = true)
    public AdminJobPostingUploadLimitSettingResponse jobPostingUploadLimit(AuthUser authUser) {
        requireAdmin(authUser);
        return AdminJobPostingUploadLimitSettingResponse.from(uploadLimitPolicy.current());
    }

    @Transactional
    public AdminJobPostingUploadLimitSettingResponse updateJobPostingUploadLimit(
            AuthUser authUser,
            AdminJobPostingUploadLimitSettingRequest request) {
        requireAdmin(authUser);
        if (request == null || request.maxBytes() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "maxBytes is required.");
        }
        return AdminJobPostingUploadLimitSettingResponse.from(
                uploadLimitPolicy.configure(request.maxBytes(), authUser.id()));
    }

    private static void requireAdmin(AuthUser authUser) {
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
    }
}
