package com.careertuner.admin.prompt.service;

import org.springframework.stereotype.Service;

import com.careertuner.admin.prompt.dto.AdminPromptView;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.companyanalysis.ai.prompt.CompanyAnalysisPromptCatalog;
import com.careertuner.jobanalysis.ai.prompt.JobAnalysisPromptCatalog;

@Service
public class AdminPromptService {

    public AdminPromptView jobAnalysis(AuthUser authUser) {
        requireAdmin(authUser);
        return JobAnalysisPromptCatalog.view();
    }

    public AdminPromptView companyAnalysis(AuthUser authUser) {
        requireAdmin(authUser);
        return CompanyAnalysisPromptCatalog.view();
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}
