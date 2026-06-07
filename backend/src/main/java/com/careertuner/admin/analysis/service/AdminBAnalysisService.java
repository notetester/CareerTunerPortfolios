package com.careertuner.admin.analysis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.analysis.dto.AdminAiUsageLogRow;
import com.careertuner.admin.analysis.dto.AdminCompanyAnalysisRow;
import com.careertuner.admin.analysis.dto.AdminJobAnalysisRow;
import com.careertuner.admin.analysis.mapper.AdminBAnalysisMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminBAnalysisService {

    private final AdminBAnalysisMapper mapper;

    @Transactional(readOnly = true)
    public List<AdminJobAnalysisRow> jobAnalyses(AuthUser authUser, int limit) {
        requireAdmin(authUser);
        return mapper.findJobAnalyses(normalizeLimit(limit));
    }

    @Transactional(readOnly = true)
    public List<AdminCompanyAnalysisRow> companyAnalyses(AuthUser authUser, int limit) {
        requireAdmin(authUser);
        return mapper.findCompanyAnalyses(normalizeLimit(limit));
    }

    @Transactional(readOnly = true)
    public List<AdminAiUsageLogRow> usageLogs(AuthUser authUser, int limit) {
        requireAdmin(authUser);
        return mapper.findBUsageLogs(normalizeLimit(limit));
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }
}
