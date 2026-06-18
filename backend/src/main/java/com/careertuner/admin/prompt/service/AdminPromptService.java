package com.careertuner.admin.prompt.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.careertuner.admin.prompt.dto.AdminPromptView;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.companyanalysis.ai.prompt.CompanyAnalysisPromptCatalog;
import com.careertuner.interview.ai.prompt.InterviewPromptCatalog;
import com.careertuner.jobanalysis.ai.prompt.JobAnalysisPromptCatalog;
import com.careertuner.profile.ai.prompt.ProfilePromptCatalog;

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

    public AdminPromptView profile(AuthUser authUser) {
        requireAdmin(authUser);
        return ProfilePromptCatalog.view();
    }

    /** 면접(D) 프롬프트는 9종이라 단일 view 가 아닌 목록으로 노출한다. */
    public List<AdminPromptView> interview(AuthUser authUser) {
        requireAdmin(authUser);
        return InterviewPromptCatalog.views();
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}
