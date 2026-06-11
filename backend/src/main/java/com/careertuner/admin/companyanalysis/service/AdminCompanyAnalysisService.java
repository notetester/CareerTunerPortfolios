package com.careertuner.admin.companyanalysis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisMetadataRequest;
import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisRow;
import com.careertuner.admin.companyanalysis.mapper.AdminCompanyAnalysisMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminCompanyAnalysisService {

    private final AdminCompanyAnalysisMapper mapper;
    private final CompanyAnalysisMapper companyAnalysisMapper;

    @Transactional(readOnly = true)
    public List<AdminCompanyAnalysisRow> companyAnalyses(AuthUser authUser, int limit) {
        requireAdmin(authUser);
        return mapper.findCompanyAnalyses(normalizeLimit(limit));
    }

    @Transactional
    public void updateMemo(AuthUser authUser, Long analysisId, String adminMemo) {
        requireAdmin(authUser);
        int updated = companyAnalysisMapper.updateAdminMemo(analysisId, blankToNull(adminMemo));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "기업 분석을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void updateMetadata(AuthUser authUser, Long analysisId, AdminCompanyAnalysisMetadataRequest request) {
        requireAdmin(authUser);
        String sourceType = requiredSourceType(request.sourceType());
        int updated = mapper.updateMetadata(
                analysisId,
                sourceType,
                request.checkedAt(),
                request.refreshRecommendedAt(),
                Boolean.TRUE.equals(request.clearCheckedAt()),
                Boolean.TRUE.equals(request.clearRefreshRecommendedAt()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "기업 분석을 찾을 수 없습니다.");
        }
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requiredSourceType(String value) {
        String sourceType = blankToNull(value);
        if (sourceType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sourceType 값은 필수입니다.");
        }
        return sourceType;
    }
}
