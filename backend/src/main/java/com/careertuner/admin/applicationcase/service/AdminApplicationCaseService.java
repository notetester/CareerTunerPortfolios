package com.careertuner.admin.applicationcase.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.aiusage.mapper.AdminAiUsageMapper;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseDetail;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseRow;
import com.careertuner.admin.applicationcase.dto.AdminStatusUpdateRequest;
import com.careertuner.admin.applicationcase.mapper.AdminApplicationCaseMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.mapper.JobPostingMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminApplicationCaseService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "ANALYZING", "READY", "APPLIED", "CLOSED");

    private final AdminApplicationCaseMapper mapper;
    private final ApplicationCaseMapper applicationCaseMapper;
    private final JobPostingMapper jobPostingMapper;
    private final JobAnalysisMapper jobAnalysisMapper;
    private final CompanyAnalysisMapper companyAnalysisMapper;
    private final AdminAiUsageMapper aiUsageMapper;

    @Transactional(readOnly = true)
    public List<AdminApplicationCaseRow> applicationCases(AuthUser authUser, String keyword, String status,
                                                          boolean includeArchived, boolean includeDeleted, int limit) {
        requireAdmin(authUser);
        return mapper.findApplicationCases(blankToNull(keyword), normalizeStatus(status, false),
                includeArchived, includeDeleted, normalizeLimit(limit));
    }

    @Transactional(readOnly = true)
    public AdminApplicationCaseDetail detail(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminApplicationCaseRow applicationCase = mapper.findApplicationCase(id);
        if (applicationCase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
        }
        return new AdminApplicationCaseDetail(
                applicationCase,
                jobPostingMapper.findJobPostingRevisionsByCaseId(id).stream().map(JobPostingResponse::from).toList(),
                jobAnalysisMapper.findJobAnalysisHistoryByCaseId(id).stream().map(JobAnalysisResponse::from).toList(),
                companyAnalysisMapper.findCompanyAnalysisHistoryByCaseId(id).stream().map(CompanyAnalysisResponse::from).toList(),
                aiUsageMapper.findBUsageLogsByCaseId(id, 100));
    }

    @Transactional
    public AdminApplicationCaseRow updateStatus(AuthUser authUser, Long id, AdminStatusUpdateRequest request) {
        requireAdmin(authUser);
        AdminApplicationCaseRow existing = mapper.findApplicationCase(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
        }
        String nextStatus = normalizeStatus(request.status(), true);
        int updated = mapper.updateStatus(id, nextStatus);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
        }
        applicationCaseMapper.insertStatusHistory(id, authUser.id(), existing.getStatus(), nextStatus, blankToNull(request.memo()));
        return mapper.findApplicationCase(id);
    }

    private static void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }

    private static String normalizeStatus(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "status 값이 필요합니다.");
            }
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "status 값이 올바르지 않습니다.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
